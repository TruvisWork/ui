-- Strategy: Read each base table ONCE, apply ALL transformations in separate CTEs
-- to force BigQuery to materialize intermediate results

-- CTE 1: Single scan of cm_event_arrival with ALL variations needed
WITH cm_event_arrival_all_cases AS (
  SELECT
    id.identifier,
    id.timestamp,
    id.channelId,
    id.payload.schema.customer_portfolio_region,
    id.payload.schema.customer_portfolio_country,
    id.payload.schema.customer_portfolio_class,
    id.payload.schema.event_type,
    timestamp AS event_timestamp,
    updatedTimestamp,
    alert,
    -- Pre-compute row numbers for ALL use cases in one pass
    ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(timestamp) ASC) AS rownum_base,
    ROW_NUMBER() OVER(PARTITION BY id.identifier, alert ORDER BY TIMESTAMP(timestamp) ASC) AS rownum_alert,
    ROW_NUMBER() OVER(PARTITION BY id.identifier, 
                      CASE WHEN LOWER(id.payload.schema.event_type) IN ('transfer_initiation', 'feedback', 'info') THEN 1 ELSE 0 END 
                      ORDER BY TIMESTAMP(timestamp) ASC) AS rownum_rules
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_arrival
  WHERE
    updatedTimestamp >= '2025-09-22 00:00:00+00'
    AND TIMESTAMP_MILLIS(id.timestamp) >= TIMESTAMP('2025-09-22 00:00:00+00')
    AND TIMESTAMP_MILLIS(id.timestamp) < TIMESTAMP('2025-10-06 16:00:00+00')
),

-- CTE 2: Single scan of event_store with ALL variations needed
event_store_all_cases AS (
  SELECT
    lifecycle_id,
    channel_type,
    source,
    sender_transaction_currency,
    sender_transaction_amount_dbl,
    customer_id,
    payment_message_source,
    segment_channel_type,
    fdz_channel,
    event_type,
    entity_type,
    payment_revision_code,
    top_payee_payer,
    channel_name,
    sender_transaction_type,
    customer_type,
    customer_id_number,
    rules_triggered,
    outcomes_and_scores,
    event_occurred_at,
    bq_insert_timestamp,
    -- Pre-compute row numbers for ALL use cases in one pass
    ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY event_occurred_at ASC) AS rownum_base,
    ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY bq_insert_timestamp ASC) AS rownum_alert,
    ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY event_occurred_at DESC) AS rownum_rules
  FROM AMH_FZ_FDR_DEV_SIT.event_store
  WHERE bq_insert_timestamp >= '2025-09-22 00:00:00+00'
),

-- CTE 3: Single scan of cm_event_state_updates with ALL variations
cm_event_state_updates_all_cases AS (
  SELECT
    identifier,
    statemachineid,
    state.id AS state_id,
    channelId,
    updatedAt,
    updatedTimestamp,
    -- Pre-compute row numbers for different use cases
    ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum_main,
    ROW_NUMBER() OVER(PARTITION BY identifier, 
                      CASE WHEN LOWER(stateMachineId) = 'status' THEN 1 ELSE 0 END 
                      ORDER BY updatedAt DESC) AS rownum_status
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
  LEFT JOIN UNNEST(ids)
  WHERE updatedTimestamp >= '2025-09-22 00:00:00+00'
),

-- CTE 4: Single scan of cm_event_assignee_update
cm_event_assignee_update_dedup AS (
  SELECT
    identifier,
    updatedAt,
    updatedTimestamp
  FROM (
    SELECT
      identifier,
      updatedAt,
      updatedTimestamp,
      ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum
    FROM AMH_FZ_FDR_DEV_SIT.cm_event_assignee_update
    LEFT JOIN UNNEST(ids)
    WHERE updatedTimestamp >= '2025-09-22 00:00:00+00'
  )
  WHERE rownum = 1
),

-- CTE 5: Single scan of cm_event_queue_changed
cm_event_queue_changed_dedup AS (
  SELECT
    identifier,
    timestamp
  FROM (
    SELECT
      timestamp,
      identifier,
      ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY TIMESTAMP(timestamp) DESC) AS rownum
    FROM AMH_FZ_FDR_DEV_SIT.cm_event_queue_changed
    LEFT JOIN UNNEST(ids)
    WHERE updatedTimestamp >= '2025-09-22 00:00:00+00'
  )
  WHERE rownum = 1
),

-- CTE 6: Single scan of workflow_rules_vw
workflow_rules AS (
  SELECT id, name
  FROM AMH_FZ_FDR_DEV_SIT.workflow_rules_vw
),

-- Now filter the pre-computed row numbers instead of rescanning
-- CTE 7: Base cm_event_arrival
cm_event_arrival AS (
  SELECT
    identifier,
    timestamp,
    channelId,
    customer_portfolio_region,
    customer_portfolio_country,
    customer_portfolio_class,
    event_type,
    event_timestamp,
    updatedTimestamp
  FROM cm_event_arrival_all_cases
  WHERE rownum_base = 1
),

-- CTE 8: Alerted cm_event_arrival
cm_event_arrival_alert AS (
  SELECT
    identifier,
    timestamp,
    updatedTimestamp
  FROM cm_event_arrival_all_cases
  WHERE alert = TRUE AND rownum_alert = 1
),

-- CTE 9: Base event_store
event_store AS (
  SELECT
    lifecycle_id,
    channel_type,
    source,
    sender_transaction_currency,
    sender_transaction_amount_dbl,
    customer_id,
    payment_message_source,
    segment_channel_type,
    fdz_channel,
    event_type,
    entity_type,
    payment_revision_code,
    top_payee_payer,
    channel_name,
    sender_transaction_type,
    customer_type,
    customer_id_number,
    rules_triggered,
    outcomes_and_scores,
    event_occurred_at,
    bq_insert_timestamp
  FROM event_store_all_cases
  WHERE 
    rownum_base = 1
    AND LOWER(event_type) IN ('transfer_initiation', 'feedback')
    AND JSON_EXTRACT_SCALAR(outcomes_and_scores, "$.decision.outcomeDecision") IN ('approve', 'decline', 'review')
),

-- CTE 10: Event_store for alerts
event_store_alert AS (
  SELECT
    lifecycle_id,
    sender_transaction_amount_dbl,
    customer_id,
    customer_type,
    customer_id_number,
    bq_insert_timestamp
  FROM event_store_all_cases
  WHERE 
    rownum_alert = 1
    AND LOWER(event_type) IN ('transfer_initiation')
),

-- CTE 11: Main state updates
cm_event_state_updates AS (
  SELECT
    identifier,
    statemachineid,
    updatedAt,
    updatedTimestamp
  FROM cm_event_state_updates_all_cases
  WHERE
    rownum_main = 1
    AND LOWER(state_id) NOT IN ("closed")
    AND LOWER(channelId) IN ("transfers")
    AND LOWER(statemachineid) NOT IN ('breach_status', 'decision', 'status_digital_activity', 
                                       'status_transfers', 'transfer_status', 'operational_status')
),

-- CTE 12: Status state updates
cm_event_state_updates_status AS (
  SELECT
    identifier,
    state_id,
    updatedAt,
    updatedTimestamp
  FROM cm_event_state_updates_all_cases
  WHERE
    rownum_status = 1
    AND LOWER(stateMachineId) = 'status'
    AND TIMESTAMP_MILLIS(updatedAt) >= '2025-09-22 00:00:00+00'
    AND TIMESTAMP_MILLIS(updatedAt) < '2025-10-06 16:00:00+00'
),

-- CTE 13: Rules aggregation using pre-scanned data
event_store_with_rules AS (
  SELECT
    es.lifecycle_id,
    TRIM(rules_split) AS rules_triggered
  FROM cm_event_arrival_all_cases cm_arr
  INNER JOIN (
    SELECT
      lifecycle_id,
      rules_split
    FROM event_store_all_cases
    LEFT JOIN UNNEST(SPLIT(rules_triggered, ';')) AS rules_split
    WHERE 
      rownum_rules = 1
      AND LOWER(event_type) IN ('transfer_initiation', 'feedback')
  ) es
  ON cm_arr.identifier = es.lifecycle_id
  WHERE 
    cm_arr.rownum_rules = 1
    AND LOWER(cm_arr.event_type) IN ('transfer_initiation', 'feedback', 'info')
),

rules AS (
  SELECT
    esr.lifecycle_id,
    STRING_AGG(wr.name) AS rule_metadata_names
  FROM event_store_with_rules esr
  INNER JOIN workflow_rules wr
  ON esr.rules_triggered = wr.id
  GROUP BY esr.lifecycle_id
),

-- CTE 14: Final aggregated dataset
final_set AS (
  SELECT
    TIMESTAMP_TRUNC(TIMESTAMP_ADD(TIMESTAMP_MILLIS(cm_event_arrival.timestamp), INTERVAL 8 hour), day) AS create_timestamp,
    NULLIF(
      TIMESTAMP_TRUNC(
        GREATEST(
          COALESCE(TIMESTAMP_MILLIS(cm_event_assignee_update_dedup.updatedAt), '1970-01-01 00:00:00 UTC'),
          COALESCE(TIMESTAMP_MILLIS(cm_event_state_updates.updatedAt), '1970-01-01 00:00:00 UTC'),
          COALESCE(TIMESTAMP(cm_event_arrival.event_timestamp), '1970-01-01 00:00:00 UTC'),
          COALESCE(TIMESTAMP(cm_event_queue_changed_dedup.timestamp), '1970-01-01 00:00:00 UTC')
        ), 
        hour
      ), 
      '1970-01-01 00:00:00'
    ) AS action_on_alert_timestamp,
    CASE
      WHEN LOWER(cm_event_state_updates_status.state_id) = "genuine" 
        OR LOWER(cm_event_state_updates_status.state_id) = "fraud" 
      THEN TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(cm_event_state_updates_status.updatedAt), hour)
    END AS result_on_alert_timestamp,
    cm_event_arrival.customer_portfolio_region AS entity,
    cm_event_arrival.customer_portfolio_country AS portfolio,
    event_store.channel_type AS channel,
    cm_event_arrival.customer_portfolio_class AS class,
    CASE
      WHEN LOWER(cm_event_arrival.customer_portfolio_country) = 'cd' 
        AND LOWER(cm_event_arrival.customer_portfolio_class) = 'pers' THEN 'CIIOM WPB'
      WHEN LOWER(cm_event_arrival.customer_portfolio_country) = 'cd'
        AND LOWER(cm_event_arrival.customer_portfolio_class) = 'buss' THEN 'CIIOM CMB'
      WHEN LOWER(cm_event_arrival.customer_portfolio_country) = 'ms' 
        AND LOWER(cm_event_arrival.customer_portfolio_class) = 'pers' THEN 'M&S WPB'
      WHEN LOWER(cm_event_arrival.customer_portfolio_country) = 'fd'
        AND LOWER(cm_event_arrival.customer_portfolio_class) = 'pers' THEN 'First Direct WPB'
      WHEN LOWER(cm_event_arrival.customer_portfolio_country) = 'uk' 
        AND LOWER(cm_event_arrival.customer_portfolio_class) = 'pers' THEN 'Red Brand WPB'
      WHEN LOWER(cm_event_arrival.customer_portfolio_country) = 'uk'
        AND LOWER(cm_event_arrival.customer_portfolio_class) = 'buss' THEN 'Red Brand CMB'
      WHEN LOWER(cm_event_arrival.customer_portfolio_country) = 'uk' 
        AND LOWER(cm_event_arrival.customer_portfolio_class) = 'priv' THEN 'Red Brand GPB'
    END AS final_portfolio,
    cm_event_state_updates.statemachineid AS statemachineID,
    cm_event_state_updates_status.state_id AS final_state,
    event_store.source AS payment_source,
    CASE
      WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores, "$.decision.outcomeDecision") = "approve" THEN "approved"
      WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores, "$.decision.outcomeDecision") = "review" THEN "review"
      WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores, "$.decision.outcomeDecision") = "decline" THEN "declined"
    END AS transaction_status,
    event_store.sender_transaction_currency,
    COUNT(DISTINCT event_store.customer_id) AS number_of_payment_customers,
    SUM(event_store.sender_transaction_amount_dbl) AS sum_of_transaction_amount_hkd,
    SUM(event_store.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp,
    SUM(CAST(event_store.sender_transaction_amount_dbl AS numeric) * 100) AS sum_of_transaction_original_amount,
    COUNT(DISTINCT cm_event_arrival_alert.identifier) AS count_of_alerts,
    COUNT(DISTINCT cm_event_arrival.identifier) AS count_of_transactions,
    SUM(event_store_alert.sender_transaction_amount_dbl) AS sum_of_transaction_amount_by_alerts,
    COUNT(DISTINCT event_store_alert.customer_id) AS number_of_alerted_customers,
    SUM(CAST(event_store_alert.sender_transaction_amount_dbl AS numeric) * 100) AS sum_of_transaction_original_amount_by_alerts,
    SUM(event_store_alert.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp_by_alerts,
    CASE
      WHEN (
        LOWER(event_store.payment_message_source) IN ("hk_gpb_mob", "hk_gpb_web") 
        AND LOWER(event_store.segment_channel_type) = 'w' 
        AND LOWER(event_store.fdz_channel) = 'transfers' 
        AND LOWER(event_store.event_type) = 'transfer_initiation' 
        AND (LOWER(event_store.entity_type) != 'b' OR event_store.entity_type IS NULL) 
        AND LOWER(event_store.payment_revision_code) = 'o' 
        AND LOWER(event_store.top_payee_payer) IN ('e', 'b', 'n', 'd')
      ) OR (
        LOWER(event_store.channel_name) = 'l' 
        AND LOWER(event_store.sender_transaction_type) = 'bt'
      ) THEN 'GPB'
      ELSE (
        CASE
          WHEN (
            LOWER(COALESCE(event_store.customer_type, event_store_alert.customer_type)) = 'b' 
            AND LOWER(SUBSTR(COALESCE(event_store.customer_id_number, event_store_alert.customer_id_number), 1, 2)) NOT IN ('xw', 'xk', 'xc')
          ) THEN "WSB"
          WHEN (
            LOWER(COALESCE(event_store.customer_type, event_store_alert.customer_type)) = 'i'
            OR (
              LOWER(COALESCE(event_store.customer_type, event_store_alert.customer_type)) = 'b'
              AND LOWER(SUBSTR(COALESCE(event_store.customer_id_number, event_store_alert.customer_id_number), 1, 2)) IN ('xw', 'xk', 'xc')
            )
          ) THEN "WPB"
        END
      )
    END AS lob
  FROM cm_event_arrival
  LEFT JOIN event_store
    ON cm_event_arrival.identifier = event_store.lifecycle_id
  LEFT JOIN cm_event_arrival_alert
    ON cm_event_arrival.identifier = cm_event_arrival_alert.identifier
  LEFT JOIN event_store_alert
    ON cm_event_arrival_alert.identifier = event_store_alert.lifecycle_id
  LEFT JOIN cm_event_state_updates
    ON cm_event_arrival.identifier = cm_event_state_updates.identifier
  LEFT JOIN cm_event_state_updates_status
    ON cm_event_arrival.identifier = cm_event_state_updates_status.identifier
  LEFT JOIN cm_event_assignee_update_dedup
    ON cm_event_arrival.identifier = cm_event_assignee_update_dedup.identifier
  LEFT JOIN cm_event_queue_changed_dedup
    ON cm_event_arrival.identifier = cm_event_queue_changed_dedup.identifier
  LEFT JOIN rules
    ON rules.lifecycle_id = cm_event_arrival.identifier
  WHERE LOWER(cm_event_arrival.channelId) IN ('transfers')
  GROUP BY
    create_timestamp,
    action_on_alert_timestamp,
    result_on_alert_timestamp,
    entity,
    portfolio,
    channel,
    lob,
    class,
    final_portfolio,
    statemachineID,
    final_state,
    payment_source,
    transaction_status,
    sender_transaction_currency
)

-- Final INSERT statement
INSERT INTO AMH_FZ_REPORT_MARTS_TABLES_DEV.Payment_Mart (
  report_name,
  create_timestamp,
  action_on_alert_timestamp,
  result_on_alert_timestamp,
  entity,
  portfolio,
  channel,
  class,
  final_portfolio,
  statemachineID,
  final_state,
  payment_source,
  transaction_status,
  sender_transaction_currency,
  number_of_payment_customers,
  sum_of_transaction_amount_usd,
  sum_of_transaction_original_amount,
  count_of_transactions,
  count_of_alerts,
  sum_of_transaction_amount_by_alerts,
  number_of_alerted_customers,
  sum_of_transaction_original_amount_by_alerts,
  sum_of_transaction_amount_gbp,
  sum_of_transaction_amount_gbp_by_alerts,
  lob,
  load_datetime
)
SELECT
  "Payment MI" AS report_name,
  create_timestamp,
  action_on_alert_timestamp,
  result_on_alert_timestamp,
  entity,
  portfolio,
  CASE
    WHEN LOWER(channel) = 'c' THEN 'Payment Card at Card Reader Terminal (including online purchase and ATM)'
    WHEN LOWER(channel) = 'd' THEN 'Payment Card or Number with Online Details and Device Fingerprint Information'
    WHEN LOWER(channel) = 'e' THEN 'Payment Card or Number with Online Details'
    WHEN LOWER(channel) = 'o' THEN 'Online Banking (internet, mobile phone)'
    WHEN LOWER(channel) = 'w' THEN 'Online Banking with device fingerprint information'
    WHEN LOWER(channel) = 'p' THEN 'Phone Banking'
    WHEN LOWER(channel) = 'h' THEN 'Self Bank Branch'
    WHEN LOWER(channel) = 'm' THEN 'Correspondence(for non-mon and check deposit)'
    WHEN LOWER(channel) = 'b' THEN 'Bank Processing(include bank initiated non-mon maintenance, ACH debit, EFT processing)'
    WHEN LOWER(channel) = 'f' THEN 'Financial Consultant'
    WHEN LOWER(channel) = 'r' THEN 'Other'
    WHEN LOWER(channel) = 's' THEN 'Merchant - Acquirer Processing with Device Fingerprint'
    WHEN LOWER(channel) = 't' THEN 'Merchant - Acquirer Processing'
    WHEN LOWER(channel) = 'u' THEN 'Unknown'
    WHEN LOWER(channel) = 'n' THEN 'NA'
    ELSE channel
  END AS channel,
  class,
  final_portfolio,
  statemachineID,
  final_state,
  payment_source,
  transaction_status,
  sender_transaction_currency,
  number_of_payment_customers,
  sum_of_transaction_amount_hkd AS sum_of_transaction_amount_usd,
  sum_of_transaction_original_amount,
  count_of_transactions,
  count_of_alerts,
  sum_of_transaction_amount_by_alerts,
  number_of_alerted_customers,
  sum_of_transaction_original_amount_by_alerts,
  sum_of_transaction_amount_gbp,
  sum_of_transaction_amount_gbp_by_alerts,
  lob,
  TIMESTAMP('2025-10-06 16:00:00+00') AS load_datetime
FROM final_set;
