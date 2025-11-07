-- Step 1: Create temp table for base cm_event_arrival
CREATE TEMP TABLE temp_cm_event_arrival AS
SELECT
  id.identifier,
  id.timestamp,
  id.channelId,
  id.payload.schema.customer_portfolio_region,
  id.payload.schema.customer_portfolio_country,
  id.payload.schema.customer_portfolio_class,
  id.payload.schema.event_type,
  timestamp AS event_timestamp,
  updatedTimestamp
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(timestamp) ASC) AS rownum
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_arrival
  WHERE
    updatedTimestamp >= '2025-09-22 00:00:00+00'
    AND TIMESTAMP_MILLIS(id.timestamp) >= TIMESTAMP('2025-09-22 00:00:00+00')
    AND TIMESTAMP_MILLIS(id.timestamp) < TIMESTAMP('2025-10-06 16:00:00+00')
)
WHERE rownum = 1;

-- Step 2: Create temp table for event_store
CREATE TEMP TABLE temp_event_store AS
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
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY event_occurred_at ASC) AS row_num
  FROM AMH_FZ_FDR_DEV_SIT.event_store
  WHERE
    LOWER(event_type) IN ('transfer_initiation', 'feedback')
    AND JSON_EXTRACT_SCALAR(outcomes_and_scores, "$.decision.outcomeDecision") IN ('approve', 'decline', 'review')
    AND bq_insert_timestamp >= '2025-09-22 00:00:00+00'
)
WHERE row_num = 1;

-- Step 3: Create temp table for alerted cm_event_arrival
CREATE TEMP TABLE temp_cm_event_arrival_alert AS
SELECT
  id.identifier,
  id.timestamp,
  updatedTimestamp
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(timestamp) ASC) AS rownum
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_arrival
  WHERE
    TIMESTAMP_MILLIS(id.timestamp) >= TIMESTAMP('2025-09-22 00:00:00+00')
    AND TIMESTAMP_MILLIS(id.timestamp) < TIMESTAMP('2025-10-06 16:00:00+00')
    AND updatedTimestamp >= '2025-09-22 00:00:00+00'
    AND alert = TRUE
)
WHERE rownum = 1;

-- Step 4: Create temp table for event_store alerts
CREATE TEMP TABLE temp_event_store_alert AS
SELECT
  lifecycle_id,
  sender_transaction_amount_dbl,
  customer_id,
  customer_type,
  customer_id_number,
  bq_insert_timestamp
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY bq_insert_timestamp ASC) AS row_num
  FROM AMH_FZ_FDR_DEV_SIT.event_store
  WHERE
    bq_insert_timestamp >= '2025-09-22 00:00:00+00'
    AND LOWER(event_type) IN ('transfer_initiation')
)
WHERE row_num = 1;

-- Step 5: Create temp table for cm_event_state_updates
CREATE TEMP TABLE temp_cm_event_state_updates AS
SELECT
  identifier,
  statemachineid,
  updatedAt,
  updatedTimestamp
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
  LEFT JOIN UNNEST(ids)
  WHERE
    updatedTimestamp >= '2025-09-22 00:00:00+00'
    AND LOWER(state.id) NOT IN ("closed")
    AND LOWER(channelId) IN ("transfers")
    AND LOWER(statemachineid) NOT IN ('breach_status', 'decision', 'status_digital_activity', 
                                       'status_transfers', 'transfer_status', 'operational_status')
)
WHERE rownum = 1;

-- Step 6: Create temp table for cm_event_state_updates_status
CREATE TEMP TABLE temp_cm_event_state_updates_status AS
SELECT
  identifier,
  state.id AS state_id,
  updatedAt,
  updatedTimestamp
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
  LEFT JOIN UNNEST(ids)
  WHERE
    LOWER(stateMachineId) = 'status'
    AND TIMESTAMP_MILLIS(updatedAt) >= '2025-09-22 00:00:00+00'
    AND updatedTimestamp >= '2025-09-22 00:00:00+00'
    AND TIMESTAMP_MILLIS(updatedAt) < '2025-10-06 16:00:00+00'
)
WHERE rownum = 1;

-- Step 7: Create temp table for cm_event_assignee_update
CREATE TEMP TABLE temp_cm_event_assignee_update AS
SELECT
  identifier,
  updatedAt,
  updatedTimestamp
FROM (
  SELECT
    *,
    ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum
  FROM AMH_FZ_FDR_DEV_SIT.cm_event_assignee_update
  LEFT JOIN UNNEST(ids)
  WHERE updatedTimestamp >= '2025-09-22 00:00:00+00'
)
WHERE rownum = 1;

-- Step 8: Create temp table for cm_event_queue_changed
CREATE TEMP TABLE temp_cm_event_queue_changed AS
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
WHERE rownum = 1;

-- Step 9: Create temp table for rules aggregation
CREATE TEMP TABLE temp_rules AS
SELECT
  event_store_rule.lifecycle_id,
  STRING_AGG(rules_metadata.name) AS rule_metadata_names
FROM (
  SELECT
    event_store.lifecycle_id,
    TRIM(rules_split) AS rules_triggered
  FROM (
    SELECT
      id.identifier,
      id.timestamp,
      updatedTimestamp
    FROM (
      SELECT
        *,
        ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(timestamp) ASC) AS rownum
      FROM AMH_FZ_FDR_DEV_SIT.cm_event_arrival
      WHERE
        LOWER(id.payload.schema.event_type) IN ('transfer_initiation', 'feedback', 'info')
        AND updatedTimestamp >= TIMESTAMP('2025-09-22 00:00:00+00')
        AND updatedTimestamp < TIMESTAMP('2025-10-06 16:00:00+00')
    )
    WHERE rownum = 1
  ) cm_event_arrival
  INNER JOIN (
    SELECT
      lifecycle_id,
      rules_split
    FROM (
      SELECT
        *,
        ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY event_occurred_at DESC) AS row_num
      FROM AMH_FZ_FDR_DEV_SIT.event_store
      LEFT JOIN UNNEST(SPLIT(rules_triggered, ';')) AS rules_split
      WHERE
        bq_insert_timestamp >= '2025-09-22 00:00:00+00'
        AND LOWER(event_type) IN ('transfer_initiation', 'feedback')
    )
    WHERE row_num = 1
  ) event_store
  ON cm_event_arrival.identifier = event_store.lifecycle_id
) event_store_rule
INNER JOIN (
  SELECT id, name
  FROM AMH_FZ_FDR_DEV_SIT.workflow_rules_vw
) rules_metadata
ON event_store_rule.rules_triggered = rules_metadata.id
GROUP BY event_store_rule.lifecycle_id;

-- Step 10: Final INSERT query using temp tables
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
  final_set.create_timestamp,
  final_set.action_on_alert_timestamp,
  final_set.result_on_alert_timestamp,
  final_set.entity,
  final_set.portfolio,
  CASE
    WHEN LOWER(final_set.channel) = 'c' THEN 'Payment Card at Card Reader Terminal (including online purchase and ATM)'
    WHEN LOWER(final_set.channel) = 'd' THEN 'Payment Card or Number with Online Details and Device Fingerprint Information'
    WHEN LOWER(final_set.channel) = 'e' THEN 'Payment Card or Number with Online Details'
    WHEN LOWER(final_set.channel) = 'o' THEN 'Online Banking (internet, mobile phone)'
    WHEN LOWER(final_set.channel) = 'w' THEN 'Online Banking with device fingerprint information'
    WHEN LOWER(final_set.channel) = 'p' THEN 'Phone Banking'
    WHEN LOWER(final_set.channel) = 'h' THEN 'Self Bank Branch'
    WHEN LOWER(final_set.channel) = 'm' THEN 'Correspondence(for non-mon and check deposit)'
    WHEN LOWER(final_set.channel) = 'b' THEN 'Bank Processing(include bank initiated non-mon maintenance, ACH debit, EFT processing)'
    WHEN LOWER(final_set.channel) = 'f' THEN 'Financial Consultant'
    WHEN LOWER(final_set.channel) = 'r' THEN 'Other'
    WHEN LOWER(final_set.channel) = 's' THEN 'Merchant - Acquirer Processing with Device Fingerprint'
    WHEN LOWER(final_set.channel) = 't' THEN 'Merchant - Acquirer Processing'
    WHEN LOWER(final_set.channel) = 'u' THEN 'Unknown'
    WHEN LOWER(final_set.channel) = 'n' THEN 'NA'
    ELSE final_set.channel
  END AS channel,
  final_set.class,
  final_set.final_portfolio,
  final_set.statemachineID,
  final_set.final_state,
  final_set.payment_source,
  final_set.transaction_status,
  final_set.sender_transaction_currency,
  final_set.number_of_payment_customers,
  final_set.sum_of_transaction_amount_hkd AS sum_of_transaction_amount_usd,
  final_set.sum_of_transaction_original_amount,
  final_set.count_of_transactions,
  final_set.count_of_alerts,
  final_set.sum_of_transaction_amount_by_alerts,
  final_set.number_of_alerted_customers,
  final_set.sum_of_transaction_original_amount_by_alerts,
  final_set.sum_of_transaction_amount_gbp,
  final_set.sum_of_transaction_amount_gbp_by_alerts,
  final_set.lob,
  TIMESTAMP('2025-10-06 16:00:00+00') AS load_datetime
FROM (
  SELECT
    TIMESTAMP_TRUNC(TIMESTAMP_ADD(TIMESTAMP_MILLIS(cm_event_arrival.timestamp), INTERVAL 8 hour), day) AS create_timestamp,
    NULLIF(
      TIMESTAMP_TRUNC(
        GREATEST(
          COALESCE(TIMESTAMP_MILLIS(cm_event_assignee_update.updatedAt), '1970-01-01 00:00:00 UTC'),
          COALESCE(TIMESTAMP_MILLIS(cm_event_state_updates.updatedAt), '1970-01-01 00:00:00 UTC'),
          COALESCE(TIMESTAMP(cm_event_arrival.event_timestamp), '1970-01-01 00:00:00 UTC'),
          COALESCE(TIMESTAMP(cm_event_queue_changed.timestamp), '1970-01-01 00:00:00 UTC')
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
  FROM temp_cm_event_arrival cm_event_arrival
  LEFT JOIN temp_event_store event_store
    ON cm_event_arrival.identifier = event_store.lifecycle_id
  LEFT JOIN temp_cm_event_arrival_alert cm_event_arrival_alert
    ON cm_event_arrival.identifier = cm_event_arrival_alert.identifier
  LEFT JOIN temp_event_store_alert event_store_alert
    ON cm_event_arrival_alert.identifier = event_store_alert.lifecycle_id
  LEFT JOIN temp_cm_event_state_updates cm_event_state_updates
    ON cm_event_arrival.identifier = cm_event_state_updates.identifier
  LEFT JOIN temp_cm_event_state_updates_status cm_event_state_updates_status
    ON cm_event_arrival.identifier = cm_event_state_updates_status.identifier
  LEFT JOIN temp_cm_event_assignee_update cm_event_assignee_update
    ON cm_event_arrival.identifier = cm_event_assignee_update.identifier
  LEFT JOIN temp_cm_event_queue_changed cm_event_queue_changed
    ON cm_event_arrival.identifier = cm_event_queue_changed.identifier
  LEFT JOIN temp_rules rules
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
) final_set;
