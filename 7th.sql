INSERT INTO
  AMH_FZ_REPORT_MARTS_TABLES_DEV.Payment_Mart ( report_name,
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
    load_datetime ) (
  SELECT
    "Payment MI EOD" AS report_name!,
    final_set11.create_timestamp AS create_timestamp,
    final_set11.action_on_alert_timestamp AS action_on_alert_timestamp,
    final_set11.result_on_alert_timestamp AS result_on_alert_timestamp,
    final_set11.entity AS entity,
    final_set11.portfolio AS portfolio,
    CASE
      WHEN LOWER(final_set1.channel) = 'c' THEN 'Payment Card at Card Reader Terminal (including online purchase and ATM)'
      WHEN LOWER(final_set1.channel) = 'd' THEN 'Payment Card or Number with Online Details and Device Fingerprint Information'
      WHEN LOWER(final_set1.channel) = 'e' THEN 'Payment Card or Number with Online Details'
      WHEN LOWER(final_set1.channel) = 'o' THEN 'Online Banking (internet, mobile phone)'
      WHEN LOWER(final_set1.channel) = 'w' THEN 'Online Banking with device fingerprint information'
      WHEN LOWER(final_set1.channel) = 'p' THEN 'Phone Banking'
      WHEN LOWER(final_set1.channel) = 'h' THEN 'Self Bank Branch'
      WHEN LOWER(final_set1.channel) = 'm' THEN 'Correspondence(for non-mon and check deposit)'
      WHEN LOWER(final_set1.channel) = 'b' THEN 'Bank Processing(include bank initiated non-mon maintenance, ACH debit, EFT processing)'
      WHEN LOWER(final_set1.channel) = 'f' THEN 'Financial Consultant'
      WHEN LOWER(final_set1.channel) = 'r' THEN 'Other'
      WHEN LOWER(final_set1.channel) = 's' THEN 'Merchant - Acquirer Processing with Device Fingerprint'
      WHEN LOWER(final_set1.channel) = 't' THEN 'Merchant - Acquirer Processing'
      WHEN LOWER(final_set1.channel) = 'u' THEN 'Unknown'
      WHEN LOWER(final_set1.channel) = 'n' THEN 'NA'
      ELSE final_set1.channel
  END
    AS channel,
    final_set1.class AS class,
    final_set1.final_portfolio AS final_portfolio,
    final_set1.statemachineID AS statemachineID,
    final_set1.final_state AS final_state,
    final_set1.payment_source AS payment_source,
    final_set1.transaction_status AS transaction_status,
    final_set1.sender_transaction_currency AS sender_transaction_currency,
    final_set1.number_of_payment_customers AS number_of_payment_customers,
    final_set1.sum_of_transaction_amount_hkd AS sum_of_transaction_amount_usd,
    final_set1.sum_of_transaction_original_amount AS sum_of_transaction_original_amount,
    final_set1.count_of_transactions AS count_of_transactions,
    final_set1.count_of_alerts AS count_of_alerts,
    final_set1.sum_of_transaction_amount_by_alerts AS sum_of_transaction_amount_by_alerts,
    final_set1.number_of_alerted_customers AS number_of_alerted_customers,
    final_set1.sum_of_transaction_original_amount_by_alerts AS sum_of_transaction_original_amount_by_alerts,
    final_set1.sum_of_transaction_amount_gbp AS sum_of_transaction_amount_gbp,
    final_set1.sum_of_transaction_amount_gbp_by_alerts AS sum_of_transaction_amount_gbp_by_alerts,
    lob,
    TIMESTAMP('2025-10-06 16:00:00+00') AS load_datetime
  FROM (
    SELECT
      TIMESTAMP_TRUNC(TIMESTAMP_ADD(TIMESTAMP_MILLIS(cm_event_arrival.id.timestamp),INTERVAL 8 hour),day) AS create_timestamp,
      NULLIF(TIMESTAMP_TRUNC(GREATEST(COALESCE(TIMESTAMP_MILLIS(cm_event_assignee_update.updatedAt),'1970-01-01 00:00:00 UTC'),COALESCE(TIMESTAMP_MILLIS(cm_event_state_updates.updatedAt),'1970-01-01 00:00:00 UTC'),COALESCE(TIMESTAMP(cm_event_arrival.timestamp),'1970-01-01 00:00:00 UTC'),COALESCE(TIMESTAMP(cm_event_queue_changed.timestamp),'1970-01-01 00:00:00 UTC')), hour), '1970-01-01 00:00:00') AS action_on_alert_timestamp,
      CASE
        WHEN LOWER(cm_event_state_updates.state.id)="genuine" OR LOWER(cm_event_state_updates.state.id)="fraud" THEN TIMESTAMP_TRUNC(TIMESTAMP_MILLIS(cm_event_state_updates.updatedAt), hour)
    END
      AS result_on_alert_timestamp,
      cm_event_arrival.id.payload.schema.customer_portfolio_region AS entity,
      cm_event_arrival.id.payload.schema.customer_portfolio_country AS portfolio,
      event_store.channel_type AS channel,
      cm_event_arrival.id.payload.schema.customer_portfolio_class AS class,
      CASE
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'cd' AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class)='pers' THEN 'CIIOM WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'cd'
      AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class)='buss' THEN 'CIIOM CMB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'ms' AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class)='pers' THEN 'M&S WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'fd'
      AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class)='pers' THEN 'First Direct WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'uk' AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class)='pers' THEN 'Red Brand WPB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'uk'
      AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class)='buss' THEN 'Red Brand CMB'
        WHEN LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_country) = 'uk' AND LOWER(cm_event_arrival.id.payload.schema.customer_portfolio_class)='priv' THEN 'Red Brand GPB'
    END
      AS final_portfolio,
      cm_event_state_updates.statemachineid AS statemachineID,
      cm_event_state_updates_status.state.id AS final_state,
      event_store.source AS payment_source,
      CASE
        WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores,"$.decision.outcomeDecision") = "approve" THEN "approved"
        WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores,"$.decision.outcomeDecision")="review" THEN "review"
        WHEN JSON_EXTRACT_SCALAR(event_store.outcomes_and_scores,"$.decision.outcomeDecision")="decline" THEN "declined"
    END
      AS transaction_status,
      event_store.sender_transaction_currency AS sender_transaction_currency,
      COUNT(DISTINCT event_store.customer_id) AS number_of_payment_customers,
      SUM(event_store.sender_transaction_amount_dbl) AS sum_of_transaction_amount_hkd,
      SUM(event_store.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp,
      SUM(CAST(event_store.sender_transaction_amount_dbl AS numeric)*100) AS sum_of_transaction_original_amount,
      COUNT(DISTINCT cm_event_arrival_alert.id.identifier) AS count_of_alerts,
      COUNT(DISTINCT cm_event_arrival.id.identifier) AS count_of_transactions,
      SUM(event_store_alert.sender_transaction_amount_dbl) AS sum_of_transaction_amount_by_alerts,
      COUNT(DISTINCT event_store_alert.customer_id) AS number_of_alerted_customers,
      SUM(CAST(event_store_alert.sender_transaction_amount_dbl AS numeric)*100) AS sum_of_transaction_original_amount_by_alerts,
      SUM(event_store_alert.sender_transaction_amount_dbl) AS sum_of_transaction_amount_gbp_by_alerts,
      CASE
        WHEN (LOWER(event_store.payment_message_source) IN ("hk_gpb_mob", "hk_gpb_web") AND LOWER(event_store.segment_channel_type) = 'w' AND LOWER(event_store.fdz_channel) = 'transfers' AND LOWER(event_store.event_type) = 'transfer_initiation' AND (LOWER(event_store.entity_type) != 'b' OR event_store.entity_type IS NULL) AND LOWER(event_store.payment_revision_code) = 'o' AND LOWER(event_store.top_payee_payer) IN ('e', 'b', 'n', 'd')) OR (LOWER(event_store.channel_name) = 'l' AND LOWER(event_store.sender_transaction_type) = 'bt') THEN 'GPB'
        ELSE (
        CASE
          WHEN (LOWER(COALESCE(event_store.customer_type,event_store_alert.customer_type)) = 'b' AND LOWER(SUBSTR(COALESCE(event_store.customer_id_number,event_store_alert.customer_id_number),1,2)) NOT IN ('xw', 'xk', 'xc')) THEN "WSB"
          WHEN (LOWER(COALESCE(event_store.customer_type,event_store_alert.customer_type)) = 'i'
          OR (LOWER(COALESCE(event_store.customer_type,event_store_alert. customer_type)) = 'b'
            AND LOWER(SUBSTR(COALESCE(event_store.customer_id_number,event_store_alert.customer_id_number),1,2)) IN ('xw',
              'xk',
              'xc'))) THEN "WPB"
      END
        )
    END
      AS lob
    FROM (
      SELECT
        *
      FROM (
        SELECT
          *,
          ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(cm_event_arrival.timestamp) ASC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_arrival
        WHERE
          updatedTimestamp>= '2025-10-06 16:00:00+00'
          AND TIMESTAMP_MILLIS(id.timestamp) >= TIMESTAMP('2025-10-06 16:00:00+00')
          AND TIMESTAMP_MILLIS(id.timestamp) <TIMESTAMP('2025-10-06 16:00:00+00'))
      WHERE
        rownum=1 ) cm_event_arrival
    LEFT JOIN (
      SELECT
        *
      FROM (
        SELECT
          *,
          ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY event_occurred_at ASC ) AS row_num
        FROM
          AMH_FZ_FDR_DEV_SIT.event_store
        WHERE
          LOWER(event_type) IN ('transfer_initiation',
            'feedback')
          AND JSON_EXTRACT_SCALAR(outcomes_and_scores,"$.decision.outcomeDecision") IN ('approve',
            'decline',
            'review')
          AND bq_insert_timestamp>='2025-10-06 16:00:00+00' )
      WHERE
        row_num = 1) event_store
    ON
      cm_event_arrival.id.identifier = event_store.lifecycle_id
    LEFT JOIN (
      SELECT
        *
      FROM (
        SELECT
          *,
          ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(cm_event_arrival.timestamp) ASC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_arrival
        WHERE
          TIMESTAMP_MILLIS(id.timestamp) >= TIMESTAMP('2025-10-06 16:00:00+00')
          AND TIMESTAMP_MILLIS(id.timestamp) <TIMESTAMP('2025-10-06 16:00:00+00')
          AND updatedTimestamp>='2025-10-06 16:00:00+00'
          AND alert=TRUE)
      WHERE
        rownum=1 ) cm_event_arrival_alert
    ON
      cm_event_arrival.id.identifier=cm_event_arrival_alert.id.identifier
    LEFT JOIN (
      SELECT
        *
      FROM (
        SELECT
          *,
          ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY bq_insert_timestamp ASC ) AS row_num
        FROM
          AMH_FZ_FDR_DEV_SIT.event_store
        WHERE
          LOWER(event_type) IN ('transfer_initiation'))
      WHERE
        row_num = 1) event_store_alert
    ON
      cm_event_arrival_alert.id.identifier = event_store_alert.lifecycle_id
    LEFT JOIN (
      SELECT
        *
      FROM (
        SELECT
          *,
          ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
        LEFT JOIN
          UNNEST(ids)
        WHERE
          updatedTimestamp>='2025-10-06 16:00:00+00'
          AND LOWER(state.id) NOT IN ("closed")
          AND LOWER(channelId) IN ("transfers")
          AND LOWER(statemachineid) NOT IN ('breach_status',
            'decision',
            'status_digital_activity',
            'status_transfers',
            'transfer_status',
            'operational_status'))
      WHERE
        rownum=1 )cm_event_state_updates
    ON
      cm_event_arrival.id.identifier = cm_event_state_updates.identifier
    LEFT JOIN (
      SELECT
        *
      FROM (
        SELECT
          *,
          ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_state_updates
        LEFT JOIN
          UNNEST(ids)
        WHERE
          LOWER(stateMachineId) = 'status'
          AND TIMESTAMP_MILLIS(updatedAt)>='2025-10-06 16:00:00+00'
          AND updatedTimestamp>='2025-10-06 16:00:00+00'
          AND TIMESTAMP_MILLIS(updatedAt)<='2025-10-06 16:00:00+00')
      WHERE
        rownum=1 )cm_event_state_updates_status
    ON
      cm_event_arrival.id.identifier = cm_event_state_updates_status.identifier
    LEFT JOIN (
      SELECT
        *
      FROM (
        SELECT
          *,
          ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY updatedAt DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_assignee_update
        LEFT JOIN
          UNNEST(ids)
        WHERE
          updatedTimestamp>='2025-10-06 16:00:00+00' )
      WHERE
        rownum=1 )cm_event_assignee_update
    ON
      cm_event_arrival.id.identifier = cm_event_assignee_update.identifier
    LEFT JOIN (
      SELECT
        *
      FROM (
        SELECT
          cm_event_queue_changed.timestamp AS timestamp,
          identifier,
          ROW_NUMBER() OVER(PARTITION BY identifier ORDER BY TIMESTAMP(cm_event_queue_changed.timestamp) DESC) AS rownum
        FROM
          AMH_FZ_FDR_DEV_SIT.cm_event_queue_changed
        LEFT JOIN
          UNNEST(ids)
        WHERE
          updatedTimestamp>='2025-10-06 16:00:00+00' )
      WHERE
        rownum=1 ) cm_event_queue_changed
    ON
      cm_event_arrival.id.identifier = cm_event_queue_changed.identifier
    LEFT JOIN (
      SELECT
        event_store_rule.lifecycle_id,
        STRING_AGG(rules_metadata.name) AS rule_metadata_names
      FROM (
        SELECT
          event_store.lifecycle_id,
          TRIM(rules_split) AS rules_triggered
        FROM (
          SELECT
            *
          FROM (
            SELECT
              *,
              ROW_NUMBER() OVER(PARTITION BY id.identifier ORDER BY TIMESTAMP(cm_event_arrival.timestamp) ASC) AS rownum
            FROM
              AMH_FZ_FDR_DEV_SIT.cm_event_arrival
            WHERE
              LOWER(id.payload.schema.event_type) IN ('transfer_initiation',
                'feedback',
                'info')
              AND updatedTimestamp >= TIMESTAMP('2025-10-06 16:00:00+00')
              AND updatedTimestamp <TIMESTAMP('2025-10-06 16:00:00+00'))
          WHERE
            rownum=1 ) cm_event_arrival
        INNER JOIN (
          SELECT
            *
          FROM (
            SELECT
              *
              FROM ((
              SELECT
                *,
              ROW_NUMBER() OVER(PARTITION BY lifecycle_id ORDER BY event_occurred_at DESC) AS row_num
            FROM
              AMH_FZ_FDR_DEV_SIT.event_store
            WHERE
              bq_insert_timestamp>='2025-10-06 16:00:00+00')
          LEFT JOIN
            UNNEST(SPLIT(rules_triggered,';')) AS rules_split )
        WHERE
          LOWER(event_type) IN ('transfer_initiation',
            'feedback'))
        WHERE
          row_num = 1) event_store
        ON
          cm_event_arrival.id.identifier = event_store.lifecycle_id) event_store_rule
      INNER JOIN (
        SELECT
          id,
          name
        FROM
          AMH_FZ_FDR_DEV_SIT.workflow_rules_vw) rules_metadata
      ON
        event_store_rule.rules_triggered=rules_metadata.id
      GROUP BY
        event_store_rule.lifecycle_id) rules
    ON
      rules.lifecycle_id=cm_event_arrival.id.identifier
    LEFT JOIN
      AMH_FZ_FDR_DEV_SIT.workflow_rules_vw
    ON
      event_store.rules_triggered = workflow_rules_vw.id
    WHERE
      LOWER(cm_event_arrival.id.channelId) IN ('transfers')
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
      sender_transaction_currency ) final_set1 )