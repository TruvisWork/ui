package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.dialect.Product;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.Table;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an EntityCatalog from BigQuery Foodmart schema tables
 */
public class FoodmartCatalogBuilder {
    private EntityCatalog catalog = new EntityCatalog();
    private final Identifier dialect = Identifier.of(Product.BIG_QUERY.name, new BigQuerySqlDialect());
    private Identifier projectName = Identifier.of("test", new BigQuerySqlDialect());
    private Identifier datasetName = Identifier.of("foodmart", new BigQuerySqlDialect());

    /**
     * Creates an EntityCatalog with all tables from Foodmart BigQuery schema
     *
     * @return a populated EntityCatalog
     */
    public EntityCatalog build() {
        // Add all tables to catalog
        addSalesFactTable(catalog);
        addInventoryFactTables(catalog);
        addAggregationTables(catalog);
        addDimensionTables(catalog);

        return catalog;
    }

    public FoodmartCatalogBuilder withProjectName(String projectName) {
        this.projectName = Identifier.of(projectName, new BigQuerySqlDialect());
        return this;
    }
    public FoodmartCatalogBuilder withDatasetName(String datasetName) {
        this.datasetName = Identifier.of(datasetName, new BigQuerySqlDialect());
        return this;
    }

    public FoodmartCatalogBuilder withCatalog(EntityCatalog catalog) {
        this.catalog = catalog;
        return this;
    }

    private static Column createColumn(String columnName, int position, DataType dataType) {
        return Column.builder()
                .name(Identifier.of(columnName, new BigQuerySqlDialect()))
                .dialect(new BigQuerySqlDialect())
                .ordinalPosition(position)
                .type(dataType)
                .build();
    }

    private void addSalesFactTable(EntityCatalog catalog) {
        List<Column> salesFactColumns = createSalesFactColumns();

        // Add each sales fact table
        addSalesFactTable(catalog, "sales_fact_1997", salesFactColumns);
        addSalesFactTable(catalog, "sales_fact_1998", salesFactColumns);
        addSalesFactTable(catalog, "sales_fact_dec_1998", salesFactColumns);
    }

    private void addSalesFactTable(EntityCatalog catalog, String tableName, List<Column> columns) {
        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        Identifier tableId = Identifier.of(tableName, new BigQuerySqlDialect());
        long timestamp = System.currentTimeMillis();

        Table table = new Table(namespace, tableId, columns, timestamp);
        catalog.addEntity(table);
    }

    private void addProductClassTable(EntityCatalog catalog) {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("product_class_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("product_subcategory", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("product_category", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("product_department", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("product_family", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("product_class", new BigQuerySqlDialect()),
                columns,
                System.currentTimeMillis()
        ));
    }

    private void addStoreTable(EntityCatalog catalog) {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("store_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_type", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("region_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_name", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_number", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_street_address", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_city", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_state", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_postal_code", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_country", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_manager", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_phone", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("store_fax", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("first_opened_date", position++, DataType.create(SqlTypeName.TIMESTAMP, 0, 0)));
        columns.add(createColumn("last_remodel_date", position++, DataType.create(SqlTypeName.TIMESTAMP, 0, 0)));
        columns.add(createColumn("store_sqft", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("grocery_sqft", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("frozen_sqft", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("meat_sqft", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("coffee_bar", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));
        columns.add(createColumn("video_store", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));
        columns.add(createColumn("salad_bar", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));
        columns.add(createColumn("prepared_food", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));
        columns.add(createColumn("florist", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("store", new BigQuerySqlDialect()),
                columns,
                System.currentTimeMillis()
        ));
    }

    private void addTimeByDayTable(EntityCatalog catalog) {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("time_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("the_date", position++, DataType.create(SqlTypeName.TIMESTAMP, 0, 0)));
        columns.add(createColumn("the_day", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("the_month", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("the_year", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("day_of_month", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("week_of_year", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("month_of_year", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("quarter", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("fiscal_period", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("time_by_day", new BigQuerySqlDialect()),
                columns,
                System.currentTimeMillis()
        ));
    }

    private void addPromotionTable(EntityCatalog catalog) {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("promotion_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("promotion_district_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("promotion_name", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("media_type", position++, DataType.create(SqlTypeName.VARCHAR, 30, 0)));
        columns.add(createColumn("cost", position++, DataType.create(SqlTypeName.DECIMAL, 10, 4)));
        columns.add(createColumn("start_date", position++, DataType.create(SqlTypeName.TIMESTAMP, 0, 0)));
        columns.add(createColumn("end_date", position++, DataType.create(SqlTypeName.TIMESTAMP, 0, 0)));

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("promotion", new BigQuerySqlDialect()),
                columns,
                System.currentTimeMillis()
        ));
    }

    private static List<Column> createSalesFactColumns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("product_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("time_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("customer_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("promotion_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("store_cost", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("unit_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        return columns;
    }

    private void addInventoryFactTables(EntityCatalog catalog) {
        List<Column> inventoryFactColumns = createInventoryFactColumns();

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        long timestamp = System.currentTimeMillis();

        // Add inventory_fact_1997
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("inventory_fact_1997", new BigQuerySqlDialect()),
                inventoryFactColumns,
                timestamp
        ));

        // Add inventory_fact_1998
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("inventory_fact_1998", new BigQuerySqlDialect()),
                inventoryFactColumns,
                timestamp
        ));
    }

    private static List<Column> createInventoryFactColumns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("product_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("time_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("warehouse_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("units_ordered", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("units_shipped", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("warehouse_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("warehouse_cost", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("supply_time", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("store_invoice", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));

        return columns;
    }

    private void addDimensionTables(EntityCatalog catalog) {
        // Add customer table
        addCustomerTable(catalog);

        // Add product table
        addProductTable(catalog);

        // Add employee table
        addEmployeeTable(catalog);

        // Add promotion table
        addPromotionTable(catalog);

        // Add store table
        addStoreTable(catalog);

        // Add product class table
        addProductClassTable(catalog);

        // Add time by day table
        addTimeByDayTable(catalog);

        // Other dimension tables would be implemented similarly
    }

    private void addEmployeeTable(EntityCatalog catalog) {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("employee_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("full_name", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("first_name", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("last_name", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("position_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("position_title", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("store_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("department_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("birth_date", position++, DataType.create(SqlTypeName.DATE, 0, 0)));
        columns.add(createColumn("hire_date", position++, DataType.create(SqlTypeName.TIMESTAMP, 0, 0)));
        columns.add(createColumn("end_date", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("salary", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("supervisor_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("education_level", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("marital_status", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("gender", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("management_role", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("employee", new BigQuerySqlDialect()),
                columns,
                System.currentTimeMillis()
        ));
    }

    private void addCustomerTable(EntityCatalog catalog) {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("customer_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("account_num", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("lname", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("fname", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("mi", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("address1", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("address2", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("address3", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("address4", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("city", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("state_province", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("postal_code", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("country", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("customer_region_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("phone1", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("phone2", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("birthdate", position++, DataType.create(SqlTypeName.DATE, 0, 0)));
        columns.add(createColumn("marital_status", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("yearly_income", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("gender", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("total_children", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("num_children_at_home", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("education", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("date_accnt_opened", position++, DataType.create(SqlTypeName.DATE, 0, 0)));
        columns.add(createColumn("member_card", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("occupation", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("houseowner", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));
        columns.add(createColumn("num_cars_owned", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("fullname", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("customer", new BigQuerySqlDialect()),
                columns,
                System.currentTimeMillis()
        ));
    }


    private void addProductTable(EntityCatalog catalog) {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("product_class_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("product_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("brand_name", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("product_name", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("SKU", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("SRP", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("gross_weight", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("net_weight", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("recyclable_package", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));
        columns.add(createColumn("low_fat", position++, DataType.create(SqlTypeName.BOOLEAN, 0, 0)));
        columns.add(createColumn("units_per_case", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("cases_per_pallet", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("shelf_width", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("shelf_height", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("shelf_depth", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));

        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("product", new BigQuerySqlDialect()),
                columns,
                System.currentTimeMillis()
        ));
    }

    private void addAggregationTables(EntityCatalog catalog) {
        List<Identifier> namespace = List.of(dialect, projectName, datasetName);
        long timestamp = System.currentTimeMillis();

        // Add agg_pl_01_sales_fact_1997 table
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("agg_pl_01_sales_fact_1997", new BigQuerySqlDialect()),
                createAggPl01Columns(),
                timestamp
        ));

        // Add agg_ll_01_sales_fact_1997 table
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("agg_ll_01_sales_fact_1997", new BigQuerySqlDialect()),
                createAggLl01Columns(),
                timestamp
        ));

        // Add agg_l_03_sales_fact_1997 table
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("agg_l_03_sales_fact_1997", new BigQuerySqlDialect()),
                createAggL03Columns(),
                timestamp
        ));

        // Add agg_l_04_sales_fact_1997 table
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("agg_l_04_sales_fact_1997", new BigQuerySqlDialect()),
                createAggL04Columns(),
                timestamp
        ));

        // Add agg_l_05_sales_fact_1997 table
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("agg_l_05_sales_fact_1997", new BigQuerySqlDialect()),
                createAggL05Columns(),
                timestamp
        ));

        // Add agg_c_10_sales_fact_1997 table
        catalog.addEntity(new Table(
                namespace,
                Identifier.of("agg_c_10_sales_fact_1997", new BigQuerySqlDialect()),
                createAggC10Columns(),
                timestamp
        ));

    }

    private static List<Column> createAggPl01Columns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("product_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("time_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("customer_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_sales_sum", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("store_cost_sum", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("unit_sales_sum", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("fact_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));

        return columns;
    }

    private static List<Column> createAggLl01Columns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("product_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("time_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("customer_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("store_cost", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("unit_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("fact_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));

        return columns;
    }

    private static List<Column> createAggL03Columns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("time_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("customer_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("store_cost", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("unit_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("fact_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));

        return columns;
    }

    private static List<Column> createAggL04Columns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("time_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("store_cost", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("unit_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("customer_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("fact_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));

        return columns;
    }

    private static List<Column> createAggL05Columns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("product_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("customer_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("promotion_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_id", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("store_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("store_cost", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("unit_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("fact_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));

        return columns;
    }

    private static List<Column> createAggC10Columns() {
        List<Column> columns = new ArrayList<>();
        int position = 0;

        columns.add(createColumn("month_of_year", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("quarter", position++, DataType.create(SqlTypeName.VARCHAR, 20, 0)));
        columns.add(createColumn("the_year", position++, DataType.create(SqlTypeName.SMALLINT, 0, 0)));
        columns.add(createColumn("store_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("store_cost", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("unit_sales", position++, DataType.create(SqlTypeName.FLOAT, 10, 2)));
        columns.add(createColumn("customer_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));
        columns.add(createColumn("fact_count", position++, DataType.create(SqlTypeName.INTEGER, 0, 0)));

        return columns;
    }
}
