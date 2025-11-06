-- Optimized version: prunes early, projects only needed columns, uses QUALIFY
WITH
-- Time bounds used across CTEs
bounds AS (
  SELECT
    TIMESTAMP('2025-09-22 00:00:00+00') AS start_ts,
    TIMESTAMP('2025-10-06 16:00:00+00') AS end_ts
),

-- Base cm_event_arrival: only required fields and one row per identifier (earliest timestamp)
base_arrival AS (
  SELECT
    id.identifier,
    id.timestamp AS id_timestamp_millis,
    id.payload.schema.customer_portfolio_region AS entity,
    id.payload.schema.customer_portfolio_country AS portfolio,
    id.payload.schema.customer_portfolio_class AS class,
    id.payload.schema.event_type AS event_type,
    channelId,
    TIMESTAMP(cm_event_arrival.timestamp) AS arrival_ts,
    updatedTimestamp
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_arrival
  CROSS JOIN bounds
  WHERE
    updatedTimestamp >= bounds.start_ts
    AND TIMESTAMP_MILLIS(id.timestamp) BETWEEN bounds.start_ts AND bounds.end_ts
    AND LOWER(id.channelId) = 'transfers'
  QUALIFY ROW_NUMBER() OVER (PARTITION BY id.identifier ORDER BY TIMESTAMP(cm_event_arrival.timestamp) ASC) = 1
),

-- cm_event_arrival alerts (only identifiers with alert = TRUE), one per identifier
base_arrival_alert AS (
  SELECT
    id.identifier AS identifier,
    id.timestamp AS id_timestamp_millis,
    TIMESTAMP(cm_event_arrival.timestamp) AS arrival_ts,
    updatedTimestamp
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_arrival
  CROSS JOIN bounds
  WHERE
    TIMESTAMP_MILLIS(id.timestamp) BETWEEN bounds.start_ts AND bounds.end_ts
    AND updatedTimestamp >= bounds.start_ts
    AND alert = TRUE
  QUALIFY ROW_NUMBER() OVER (PARTITION BY id.identifier ORDER BY TIMESTAMP(cm_event_arrival.timestamp) ASC) = 1
),

-- event_store: keep only earliest (or desired) row per lifecycle_id after applying filters; project only needed fields
event_store_pruned AS (
  SELECT
    lifecycle_id,
    LOWER(event_type) AS event_type,
    bq_insert_timestamp,
    event_occurred_at,
    channel_type,
    source AS payment_source,
    sender_transaction_currency,
    sender_transaction_amount_dbl,
    customer_id,
    customer_id_number,
    customer_type,
    payment_message_source,
    segment_channel_type,
    fdz_channel,
    payment_revision_code,
    top_payee_payer,
    channel_name,
    sender_transaction_type,
    outcomes_and_scores,
    ROW_NUMBER() OVER (PARTITION BY lifecycle_id ORDER BY event_occurred_at ASC) AS rn
  FROM AMH_FZ_FDR_DEV_SIT.event_store
  CROSS JOIN bounds
  WHERE
    bq_insert_timestamp >= bounds.start_ts
    AND LOWER(event_type) IN ('transfer_initiation', 'feedback')
    AND JSON_EXTRACT_SCALAR(outcomes_and_scores, '$.decision.outcomeDecision') IN ('approve', 'decline', 'review')
)
-- pick first per lifecycle_id as original did
, event_store AS (
  SELECT * EXCEPT (rn)
  FROM event_store_pruned
  WHERE rn = 1
),

-- event_store_alert: for alerted event_store rows (used for alert-level sums/counts)
event_store_alert_pruned AS (
  SELECT
    lifecycle_id,
    sender_transaction_amount_dbl,
    customer_id,
    customer_id_number,
    customer_type,
    ROW_NUMBER() OVER (PARTITION BY lifecycle_id ORDER BY bq_insert_timestamp ASC) AS rn
  FROM AMH_FZ_FDR_DEV_SIT.event_store
  CROSS JOIN bounds
  WHERE
    bq_insert_timestamp >= bounds.start_ts
    AND LOWER(event_type) = 'transfer_initiation'
)
, event_store_alert AS (
  SELECT * EXCEPT (rn)
  FROM event_store_alert_pruned
  WHERE rn = 1
),

-- cm_event_state_updates: pick latest per identifier with filters
cm_state_updates AS (
  SELECT
    identifier,
    statemachineid,
    state.id AS state_id,
    updatedAt,
    updatedTimestamp,
    ROW_NUMBER() OVER (PARTITION BY identifier ORDER BY updatedAt DESC) AS rn
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
  LEFT JOIN UNNEST(ids)
  CROSS JOIN bounds
  WHERE
    updatedTimestamp >= bounds.start_ts
    AND LOWER(state.id) NOT IN ('closed')
    AND LOWER(channelId) = 'transfers'
    AND LOWER(statemachineid) NOT IN ('breach_status','decision','status_digital_activity','status_transfers','transfer_status','operational_status')
)
, cm_event_state_updates AS (
  SELECT * EXCEPT (rn)
  FROM cm_state_updates
  WHERE rn = 1
),

-- cm_event_state_updates_status: for final_state from stateMachineId = 'status'
cm_state_status AS (
  SELECT
    identifier,
    state.id AS state_id,
    updatedAt,
    updatedTimestamp,
    ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rn
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
  LEFT JOIN UNNEST(ids)
  CROSS JOIN bounds
  WHERE
    LOWER(stateMachineId) = 'status'
    AND TIMESTAMP_MILLIS(updatedAt) >= bounds.start_ts
    AND TIMESTAMP_MILLIS(updatedAt) < bounds.end_ts
    AND updatedTimestamp >= bounds.start_ts
)
, cm_event_state_updates_status AS (
  SELECT * EXCEPT (rn)
  FROM cm_state_status
  WHERE rn = 1
),

-- cm_event_assignee_update: latest per identifier (only updatedAt/time filter)
cm_assignee AS (
  SELECT
    identifier,
    updatedAt,
    updatedTimestamp,
    ROW_NUMBER() OVER (PARTITION BY identifier ORDER BY updatedAt DESC) AS rn
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_assignee_update
  LEFT JOIN UNNEST(ids)
  CROSS JOIN bounds
  WHERE updatedTimestamp >= bounds.start_ts
)
, cm_event_assignee_update AS (
  SELECT * EXCEPT (rn)
  FROM cm_assignee
  WHERE rn = 1
),

-- cm_event_queue_changed: latest per identifier
cm_queue_changed AS (
  SELECT
    identifier,
    cm_event_queue_changed.timestamp AS queue_ts,
    ROW_NUMBER() OVER (PARTITION BY identifier ORDER BY TIMESTAMP(cm_event_queue_changed.timestamp) DESC) AS rn
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_queue_changed
  LEFT JOIN UNNEST(ids)
  CROSS JOIN bounds
  WHERE updatedTimestamp >= bounds.start_ts
)
, cm_event_queue_changed AS (
  SELECT identifier, queue_ts
  FROM cm_queue_changed
  WHERE rn = 1
),

-- workflow rules mapping for rule names (small lookup)
rules_metadata AS (
  SELECT id, name FROM AMH_FZ_FDR_DEV_SIT.workflow_rules_vw
),

-- rules triggered per lifecycle_id: prune event_store to latest and unnest rules_split; keep only needed lifecycle_ids
event_store_rules_exploded AS (
  SELECT
    es.lifecycle_id,
    TRIM(rule_split) AS rule_triggered
  FROM (
    SELECT lifecycle_id, rules_triggered, ROW_NUMBER() OVER (PARTITION BY lifecycle_id ORDER BY event_occurred_at DESC) AS rn
    FROM AMH_FZ_FDR_DEV_SIT.event_store
    CROSS JOIN bounds
    WHERE bq_insert_timestamp >= bounds.start_ts
  ) es
  JOIN UNNEST(SPLIT(es.rules_triggered, ';')) AS rule_split
  WHERE rn = 1
),
event_store_rule_names AS (
  SELECT
    es.lifecycle_id,
    STRING_AGG(rm.name) AS rule_metadata_names
  FROM event_store_rules_exploded es
  JOIN rules_metadata rm
    ON es.rule_triggered = rm.id
  GROUP BY es.lifecycle_id
)

-- Final aggregation: join pruned CTEs and aggregate. Projection limited to required fields.
INSERT INTO AMH_FZ_REPORT_MARTS_TABLES_DEV.Payment_Mart (
  report_name, create_timestamp, action_on_alert_timestamp, result_on_alert_timestamp,
  entity, portfolio, channel, class, final_portfolio, statemachineID, final_state,
  payment_source, transaction_status, sender_transaction_currency, number_of_payment_customers,
  sum_of_transaction_amount_usd, sum_of_transaction_original_amount, count_of_transactions,
  count_of_alerts, sum_of_transaction_amount_by_alerts, number_of_alerted_customers,
  sum_of_transaction_original_amount_by_alerts, sum_of_transaction_amount_gbp,
  sum_of_transaction_amount_gbp_by_alerts, lob, load_datetime
)
SELECT
  'Payment MI' AS report_name,
  TIMESTAMP_TRUNC(TIMESTAMP_ADD(TIMESTAMP_MILLIS(a.id_timestamp_millis), INTERVAL 8 hour), DAY) AS create_timestamp,
  NULLIF(
    TIMESTAMP_TRUNC(
      GREATEST(
        COALESCE(TIMESTAMP_MILLIS(ass.updatedAt), TIMESTAMP('1970-01-01 00:00:00 UTC')),
        COALESCE(TIMESTAMP_MILLIS(su.updatedAt), TIMESTAMP('1970-01-01 00:00:00 UTC')),
        COALESCE(a.arrival_ts, TIMESTAMP('1970-01-01 00:00:00 UTC')),
        COALESCE(q.queue_ts, TIMESTAMP('1970-01-01 00:00:00 UTC'))
      ), HOUR
    ), TIMESTAMP('1970-01-01 00:00:00')
  ) AS action_on_alert_timestamp,
  CASE
    WHEN LOWER(su.state_id) IN ('genuine', 'fraud')
      THEN TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(su.updatedAt), HOUR)
  END AS result_on_alert_timestamp,
  a.entity AS entity,
  a.portfolio AS portfolio,
  es.channel_type AS channel,
  a.class AS class,
  CASE
    WHEN LOWER(a.portfolio) = 'cd' AND LOWER(a.class) = 'pers' THEN 'CIIOM WPB'
    WHEN LOWER(a.portfolio) = 'cd' AND LOWER(a.class) = 'buss' THEN 'CIIOM CMB'
    WHEN LOWER(a.portfolio) = 'ms' AND LOWER(a.class) = 'pers' THEN 'M&S WPB'
    WHEN LOWER(a.portfolio) = 'fd' AND LOWER(a.class) = 'pers' THEN 'First Direct WPB'
    WHEN LOWER(a.portfolio) = 'uk' AND LOWER(a.class) = 'pers' THEN 'Red Brand WPB'
    WHEN LOWER(a.portfolio) = 'uk' AND LOWER(a.class) = 'buss' THEN 'Red Brand CMB'
    WHEN LOWER(a.portfolio) = 'uk' AND LOWER(a.class) = 'priv' THEN 'Red Brand GPB'
  END AS final_portfolio,
  su.statemachineid AS statemachineID,
  ss.state_id AS final_state,
  es.payment_source AS payment_source,
  CASE
    WHEN JSON_EXTRACT_SCALAR(es.outcomes_and_scores, '$.decision.outcomeDecision') = 'approve' THEN 'approved'
    WHEN JSON_EXTRACT_SCALAR(es.outcomes_and_scores, '$.decision.outcomeDecision') = 'review' THEN 'review'
    WHEN JSON_EXTRACT_SCALAR(es.outcomes_and_scores, '$.decision.outcomeDecision') = 'decline' THEN 'declined'
  END AS transaction_status,
  es.sender_transaction_currency AS sender_transaction_currency,
  COUNT(DISTINCT es.customer_id) AS number_of_payment_customers,
  SUM(es.sender_transaction_amount_dbl) AS sum_of_transaction_amount_usd,
  SUM(CAST(es.sender_transaction_amount_dbl AS NUMERIC) * 100) AS sum_of_transaction_original_amount,
  COUNT(DISTINCT a.identifier) AS count_of_transactions,
  COUNT(DISTINCT aa.identifier) AS count_of_alerts,
  SUM(ea.sender_transaction_amount_dbl) AS sum_of_transaction_amount_by_alerts,
  COUNT(DISTINCT ea.customer_id) AS number_of_alerted_customers,
  SUM(CAST(ea.sender_transaction_amount_dbl AS NUMERIC) * 100) AS sum_of_transaction_original_amount_by_alerts,
  SUM(es.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp,
  SUM(ea.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp_by_alerts,
  CASE
    WHEN (
      LOWER(es.payment_message_source) IN ('hk_gpb_mob', 'hk_gpb_web')
      AND LOWER(es.segment_channel_type) = 'w'
      AND LOWER(es.fdz_channel) = 'transfers'
      AND LOWER(es.event_type) = 'transfer_initiation'
      AND (LOWER(es.entity_type) != 'b' OR es.entity_type IS NULL)
      AND LOWER(es.payment_revision_code) = 'o'
      AND LOWER(es.top_payee_payer) IN ('e','b','n','d')
    ) OR (LOWER(es.channel_name) = 'l' AND LOWER(es.sender_transaction_type) = 'bt')
    THEN 'GPB'
    ELSE (
      CASE
        WHEN LOWER(COALESCE(es.customer_type, ea.customer_type)) = 'b'
          AND LOWER(SUBSTR(COALESCE(es.customer_id_number, ea.customer_id_number), 1, 2)) NOT IN ('xw','xk','xc') THEN 'WSB'
        WHEN LOWER(COALESCE(es.customer_type, ea.customer_type)) = 'i'
          OR (LOWER(COALESCE(es.customer_type, ea.customer_type)) = 'b'
              AND LOWER(SUBSTR(COALESCE(es.customer_id_number, ea.customer_id_number), 1, 2)) IN ('xw','xk','xc')) THEN 'WPB'
      END
    )
  END AS lob,
  TIMESTAMP('2025-10-06 16:00:00+00') AS load_datetime
FROM base_arrival a
LEFT JOIN event_store es
  ON a.identifier = es.lifecycle_id
LEFT JOIN base_arrival_alert aa
  ON a.identifier = aa.identifier
LEFT JOIN event_store_alert ea
  ON aa.identifier = ea.lifecycle_id
LEFT JOIN cm_event_state_updates su
  ON a.identifier = su.identifier
LEFT JOIN cm_event_state_updates_status ss
  ON a.identifier = ss.identifier
LEFT JOIN cm_event_assignee_update ass
  ON a.identifier = ass.identifier
LEFT JOIN cm_event_queue_changed q
  ON a.identifier = q.identifier
LEFT JOIN event_store_rule_names r
  ON a.identifier = r.lifecycle_id
LEFT JOIN AMH_FZ_FDR_DEV_SIT.workflow_rules_vw wr
  ON es.rules_triggered = wr.id
GROUP BY
  create_timestamp, action_on_alert_timestamp, result_on_alert_timestamp,
  a.entity, a.portfolio, es.channel_type, lob, a.class, final_portfolio,
  su.statemachineid, ss.state_id, es.payment_source, transaction_status,
  es.sender_transaction_currency;