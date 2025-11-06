INSERT INTO
  AMH_FZ_REPORT_MARTS_TABLES_DEV.Payment_Mart (
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
(
  SELECT
    "Payment MI" AS report_name,
    final_set.create_timestamp AS create_timestamp,
    final_set.action_on_alert_timestamp AS action_on_alert_timestamp,
    final_set.result_on_alert_timestamp AS result_on_alert_timestamp,
    final_set.entity AS entity,
    final_set.portfolio AS portfolio,
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
    final_set.class AS class,
    final_set.final_portfolio AS final_portfolio,
    final_set.statemachineID AS statemachineID,
    final_set.final_state AS final_state,
    final_set.payment_source AS payment_source,
    final_set.transaction_status AS transaction_status,
    final_set.sender_transaction_currency AS sender_transaction_currency,
    final_set.number_of_payment_customers AS number_of_payment_customers,
    final_set.sum_of_transaction_amount_hkd AS sum_of_transaction_amount_usd,
    final_set.sum_of_transaction_original_amount AS sum_of_transaction_original_amount,
    final_set.count_of_transactions AS count_of_transactions,
    final_set.count_of_alerts AS count_of_alerts,
    final_set.sum_of_transaction_amount_by_alerts AS sum_of_transaction_amount_by_alerts,
    final_set.number_of_alerted_customers AS number_of_alerted_customers,
    final_set.sum_of_transaction_original_amount_by_alerts AS sum_of_transaction_original_amount_by_alerts,
    final_set.sum_of_transaction_amount_gbp AS sum_of_transaction_amount_gbp,
    final_set.sum_of_transaction_amount_gbp_by_alerts AS sum_of_transaction_amount_gbp_by_alerts,
    lob,
    TIMESTAMP('2025-10-06 16:00:00+00') AS load_datetime
  FROM (
    SELECT
      TIMESTAMP_TRUNC(
        TIMESTAMP_ADD(TIMESTAMP_MILLIS(cm_event_arrival.id.timestamp), INTERVAL 8 hour),
        day
      ) AS create_timestamp,
      NULLIF(
        TIMESTAMP_TRUNC(
          GREATEST(
            COALESCE(TIMESTAMP_MILLIS(cm_event_assignee_update.updatedAt), '1970-01-01 00:00:00 UTC'),
            COALESCE(TIMESTAMP_MILLIS(cm_event_state_updates.updatedAt), '1970-01-01 00:00:00 UTC'),
            COALESCE(TIMESTAMP(cm_event_arrival.timestamp), '1970-01-01 00:00:00 UTC'),
            COALESCE(TIMESTAMP(cm_event_queue_changed.timestamp), '1970-01-01 00:00:00 UTC')
          ),
          hour
        ),
        '1970-01-01 00:00:00'
      ) AS action_on_alert_timestamp,
      CASE
        WHEN LOWER(cm_event_state_updates.state.id) = "genuine"
          OR LOWER(cm_event_state_updates.state.id) = "fraud"
        THEN TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(cm_event_state_updates.updatedAt), hour)
      END AS result_on_alert_timestamp,
      cm_event_arrival.id.payload.schema.customer_portfolio_region AS entity,
      cm_event_arrival.id.payload.schema.customer_portfolio_country AS portfolio,
      event_store.channel_type AS channel,
      cm_event_arrival.id.payload.schema.customer_portfolio_class AS class,
      CASE
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'cd'
          AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class) = 'pers'
        THEN 'CIIOM WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'cd'
          AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class) = 'buss'
        THEN 'CIIOM CMB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'ms'
          AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class) = 'pers'
        THEN 'M&S WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'fd'
          AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class) = 'pers'
        THEN 'First Direct WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'uk'
          AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class) = 'pers'
        THEN 'Red Brand WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'uk'
          AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class) = 'buss'
        THEN 'Red Brand CMB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'uk'
          AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class) = 'priv'
        THEN 'Red Brand GPB'
      END AS final_portfolio,
      cm_event_state_updates.statemachineid AS statemachineID,
      cm_event_state_updates_status.state.id AS final_state,
      event_store.source AS payment_source,
      CASE
        WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores, "$.decision.outcomeDecision") = "approve" THEN "approved"
        WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores, "$.decision.outcomeDecision") = "review" THEN "review"
        WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores, "$.decision.outcomeDecision") = "decline" THEN "declined"
      END AS transaction_status,
      event_store.sender_transaction_currency AS sender_transaction_currency,
      COUNT(DISTINCT event_store.customer_id) AS number_of_payment_customers,
      SUM(event_store.sender_transaction_amount_dbl) AS sum_of_transaction_amount_hkd,
      SUM(event_store.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp,
      SUM(CAST(event_store.sender_transaction_amount_dbl AS numeric) * 100) AS sum_of_transaction_original_amount,
      COUNT(DISTINCT cm_event_arrival_alert.id.identifier) AS count_of_alerts,
      COUNT(DISTINCT cm_event_arrival.id.identifier) AS count_of_transactions,
      SUM(event_store.alert.sender_transaction_amount_dbl) AS sum_of_transaction_amount_by_alerts,
      COUNT(DISTINCT event_store.alert.customer_id) AS number_of_alerted_customers,
      SUM(CAST(event_store.alert.sender_transaction_amount_dbl AS numeric) * 100) AS sum_of_transaction_original_amount_by_alerts,
      SUM(event_store.alert.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp_by_alerts,
      CASE
        WHEN (
          LOWER(event_store.payment_message_source) IN ("hk_gpb_mob", "hk_gpb_web")
          AND LOWER(event_store.segment_channel_type) = 'w'
          AND LOWER(event_store.fdz_channel) = 'transfers'
          AND LOWER(event_store.event_type) = 'transfer_initiation'
          AND (LOWER(event_store.entity_type) != 'b' OR event_store.entity_type IS NULL)
          AND LOWER(event_store.payment_revision_code) = 'o'
          AND LOWER(event_store.top_payee_payer) IN ('e', 'b', 'n', 'd')
        )
        OR (LOWER(event_store.channel_name) = 'l' AND LOWER(event_store.sender_transaction_type) = 'bt')
        THEN 'GPB'
        ELSE (
          CASE
            WHEN (
              LOWER(COALESCE(event_store.customer_type, event_store.alert.customer_type)) = 'b'
              AND LOWER(SUBSTR(COALESCE(event_store.customer_id_number, event_store.alert.customer_id_number), 1, 2)) NOT IN ('xw', 'xk', 'xc')
            )
            THEN "WSB"
            WHEN (
              LOWER(COALESCE(event_store.customer_type, event_store.alert.customer_type)) = 'i'
              OR (
                LOWER(COALESCE(event_store.customer_type, event_store.alert.customer_type)) = 'b'
                AND LOWER(SUBSTR(COALESCE(event_store.customer_id_number, event_store.alert.customer_id_number), 1, 2)) IN ('xw', 'xk', 'xc')
              )
            )
            THEN "WPB"
          END
        )
      END AS lob
    FROM (
      SELECT
        cm_event_arrival.id.identifier,
        cm_event_arrival.id.timestamp,
        cm_event_arrival.id.payload.schema.customer_portfolio_region,
        cm_event_arrival.id.payload.schema.customer_portfolio_country,
        cm_event_arrival.id.payload.schema.customer_portfolio_class,
        cm_event_arrival.id.channelId,
        cm_event_arrival.timestamp,
        cm_event_arrival.updatedTimestamp,
        cm_event_arrival.alert
      FROM (
        SELECT
          id.identifier,
          id.timestamp,
          id.payload.schema.customer_portfolio_region,
          id.payload.schema.customer_portfolio_country,
          id.payload.schema.customer_portfolio_class,
          id.channelId,
          timestamp,
          updatedTimestamp,
          alert,
          ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(timestamp) ASC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_arrival
        WHERE
          updatedTimestamp >= '2025-09-22 00:00:00+00'
          AND TIMESTAMP_MILLIS(id.timestamp) >= TIMESTAMP('2025-09-22 00:00:00+00')
          AND TIMESTAMP_MILLIS(id.timestamp) < TIMESTAMP('2025-10-06 16:00:00+00')
      )
      WHERE rownum = 1
    ) cm_event_arrival
        LEFT JOIN (
      SELECT
        ids.identifier,
        ids.channelId,
        state.id AS state_id,
        stateMachineId AS statemachineid,
        updatedAt,
        updatedTimestamp
      FROM (
        SELECT
          ids.identifier,
          ids.channelId,
          state.id,
          stateMachineId,
          updatedAt,
          updatedTimestamp,
          ROW_NUMBER() OVER(PARTITION BY ids.identifier ORDER BY TIMESTAMP_MILLIS(updatedAt) DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
        WHERE
          updatedTimestamp >= '2025-09-22 00:00:00+00'
          AND TIMESTAMP_MILLIS(updatedAt) >= TIMESTAMP('2025-09-22 00:00:00+00')
          AND TIMESTAMP_MILLIS(updatedAt) < TIMESTAMP('2025-10-06 16:00:00+00')
      )
      WHERE rownum = 1
    ) cm_event_state_updates
    ON cm_event_state_updates.identifier = cm_event_arrival.id.identifier

    LEFT JOIN (
      SELECT
        ids.identifier,
        state.id AS state_id,
        stateMachineId,
        updatedAt,
        updatedTimestamp
      FROM (
        SELECT
          ids.identifier,
          state.id,
          stateMachineId,
          updatedAt,
          updatedTimestamp,
          ROW_NUMBER() OVER(PARTITION BY ids.identifier ORDER BY TIMESTAMP_MILLIS(updatedAt) DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
        WHERE
          LOWER(state.id) IN ('fraud', 'genuine')
          AND updatedTimestamp >= '2025-09-22 00:00:00+00'
          AND TIMESTAMP_MILLIS(updatedAt) >= TIMESTAMP('2025-09-22 00:00:00+00')
          AND TIMESTAMP_MILLIS(updatedAt) < TIMESTAMP('2025-10-06 16:00:00+00')
      )
      WHERE rownum = 1
    ) cm_event_state_updates_status
    ON cm_event_state_updates_status.identifier = cm_event_arrival.id.identifier

    LEFT JOIN (
      SELECT
        ids.identifier,
        updatedAt,
        updatedTimestamp
      FROM (
        SELECT
          ids.identifier,
          updatedAt,
          updatedTimestamp,
          ROW_NUMBER() OVER(PARTITION BY ids.identifier ORDER BY TIMESTAMP_MILLIS(updatedAt) DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_assignee_update
        WHERE
          updatedTimestamp >= '2025-09-22 00:00:00+00'
          AND TIMESTAMP_MILLIS(updatedAt) >= TIMESTAMP('2025-09-22 00:00:00+00')
          AND TIMESTAMP_MILLIS(updatedAt) < TIMESTAMP('2025-10-06 16:00:00+00')
      )
      WHERE rownum = 1
    ) cm_event_assignee_update
    ON cm_event_assignee_update.identifier = cm_event_arrival.id.identifier

    LEFT JOIN (
      SELECT
        ids.identifier,
        timestamp,
        updatedTimestamp
      FROM (
        SELECT
          ids.identifier,
          timestamp,
          updatedTimestamp,
          ROW_NUMBER() OVER(PARTITION BY ids.identifier ORDER BY TIMESTAMP(timestamp) DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_queue_change
        WHERE
          updatedTimestamp >= '2025-09-22 00:00:00+00'
          AND TIMESTAMP(timestamp) >= TIMESTAMP('2025-09-22 00:00:00+00')
          AND TIMESTAMP(timestamp) < TIMESTAMP('2025-10-06 16:00:00+00')
      )
      WHERE rownum = 1
    ) cm_event_queue_changed
    ON cm_event_queue_changed.identifier = cm_event_arrival.id.identifier

    LEFT JOIN (
      SELECT
        lifecycle_id,
        event_type,
        event_occurred_at,
        bq_insert_timestamp,
        rules_triggered,
        outcomes_and_scores,
        source,
        customer_id,
        sender_transaction_currency,
        sender_transaction_amount_dbl,
        payment_message_source,
        segment_channel_type,
        fdz_channel,
        entity_type,
        payment_revision_code,
        top_payee_payer,
        customer_type,
        customer_id_number,
        channel_type
      FROM
        AMH_FZ_FDR_DEV_SIT.event_store
      WHERE
        bq_insert_timestamp >= '2025-09-22 00:00:00+00'
        AND event_occurred_at >= TIMESTAMP('2025-09-22 00:00:00+00')
        AND event_occurred_at < TIMESTAMP('2025-10-06 16:00:00+00')
    ) event_store
    ON event_store.lifecycle_id = cm_event_arrival.id.payload.schema.lifecycle_id

    LEFT JOIN (
      SELECT
        lifecycle_id,
        bq_insert_timestamp,
        event_type
      FROM (
        SELECT
          lifecycle_id,
          bq_insert_timestamp,
          event_type,
          ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY bq_insert_timestamp DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.event_store
        WHERE
          event_type LIKE '%alert%'
          AND bq_insert_timestamp >= '2025-09-22 00:00:00+00'
          AND bq_insert_timestamp < '2025-10-06 16:00:00+00'
      )
      WHERE rownum = 1
    ) event_store_alert
    ON event_store_alert.lifecycle_id = cm_event_arrival.id.payload.schema.lifecycle_id

    LEFT JOIN (
      SELECT
        queueId,
        id,
        name
      FROM
        AMH_FZ_FDR_DEV_SIT.workflow_rules_vw
    ) workflow_rules_vw
    ON workflow_rules_vw.queueId = cm_event_queue_changed.queueId
  )
);

