package com.calcite_new.core.catalog;

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
public class BigQueryFoodmartCatalogBuilder {
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

  public BigQueryFoodmartCatalogBuilder withProjectName(String projectName) {
    this.projectName = Identifier.of(projectName, new BigQuerySqlDialect());
    return this;
  }

  public BigQueryFoodmartCatalogBuilder withDatasetName(String datasetName) {
    this.datasetName = Identifier.of(datasetName, new BigQuerySqlDialect());
    return this;
  }

  public BigQueryFoodmartCatalogBuilder withCatalog(EntityCatalog catalog) {
    this.catalog = catalog;
    return this;
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

    columns.add(new Column(Identifier.of("product_class_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("product_subcategory", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("product_category", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("product_department", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("product_family", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));

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

    columns.add(new Column(Identifier.of("store_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_type", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("region_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("store_name", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_number", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("store_street_address", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_city", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_state", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_postal_code", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_country", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_manager", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_phone", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("store_fax", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("first_opened_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.TIMESTAMP, 0, 0),
        true));
    columns.add(new Column(Identifier.of("last_remodel_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.TIMESTAMP, 0, 0),
        true));
    columns.add(new Column(Identifier.of("store_sqft", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("grocery_sqft", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("frozen_sqft", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("meat_sqft", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("coffee_bar", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));
    columns.add(new Column(Identifier.of("video_store", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));
    columns.add(new Column(Identifier.of("salad_bar", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));
    columns.add(new Column(Identifier.of("prepared_food", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));
    columns.add(new Column(Identifier.of("florist", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));

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

    columns.add(new Column(Identifier.of("time_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("the_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.TIMESTAMP, 0, 0),
        true));
    columns.add(new Column(Identifier.of("the_day", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("the_month", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("the_year", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        true));
    columns.add(new Column(Identifier.of("day_of_month", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        true));
    columns.add(new Column(Identifier.of("week_of_year", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("month_of_year", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        true));
    columns.add(new Column(Identifier.of("quarter", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("fiscal_period", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));

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

    columns.add(new Column(Identifier.of("promotion_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("promotion_district_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("promotion_name", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("media_type", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 30, 0),
        true));
    columns.add(new Column(Identifier.of("cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.DECIMAL, 10, 4),
        true));
    columns.add(new Column(Identifier.of("start_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.TIMESTAMP, 0, 0),
        true));
    columns.add(new Column(Identifier.of("end_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.TIMESTAMP, 0, 0),
        true));

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

    columns.add(new Column(Identifier.of("product_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("time_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("customer_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("promotion_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("store_cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("unit_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
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

    columns.add(new Column(Identifier.of("product_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("time_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("warehouse_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("units_ordered", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("units_shipped", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("warehouse_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("warehouse_cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("supply_time", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_invoice", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));

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

    columns.add(new Column(Identifier.of("employee_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("full_name", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("first_name", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("last_name", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("position_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("position_title", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("store_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("department_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("birth_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.DATE, 0, 0),
        false));
    columns.add(new Column(Identifier.of("hire_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.TIMESTAMP, 0, 0),
        false));
    columns.add(new Column(Identifier.of("end_date", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("salary", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("supervisor_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("education_level", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("marital_status", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("gender", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("management_role", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));

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

    columns.add(new Column(Identifier.of("customer_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("account_num", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("lname", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("fname", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("mi", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("address1", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("address2", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("address3", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("address4", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("city", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("state_province", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("postal_code", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("country", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("customer_region_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("phone1", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("phone2", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("birthdate", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.DATE, 0, 0),
        true));
    columns.add(new Column(Identifier.of("marital_status", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("yearly_income", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("gender", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("total_children", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        true));
    columns.add(new Column(Identifier.of("num_children_at_home", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        true));
    columns.add(new Column(Identifier.of("education", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("date_accnt_opened", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.DATE, 0, 0),
        true));
    columns.add(new Column(Identifier.of("member_card", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("occupation", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("houseowner", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));
    columns.add(new Column(Identifier.of("num_cars_owned", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        true));
    columns.add(new Column(Identifier.of("fullname", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));

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

    columns.add(new Column(Identifier.of("product_class_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("product_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("brand_name", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        true));
    columns.add(new Column(Identifier.of("product_name", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("SKU", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("SRP", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        true));
    columns.add(new Column(Identifier.of("gross_weight", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        true));
    columns.add(new Column(Identifier.of("net_weight", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        true));
    columns.add(new Column(Identifier.of("recyclable_package", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));
    columns.add(new Column(Identifier.of("low_fat", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.BOOLEAN, 0, 0),
        true));
    columns.add(new Column(Identifier.of("units_per_case", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        true));
    columns.add(new Column(Identifier.of("cases_per_pallet", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        true));
    columns.add(new Column(Identifier.of("shelf_width", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        true));
    columns.add(new Column(Identifier.of("shelf_height", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        true));
    columns.add(new Column(Identifier.of("shelf_depth", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        true));

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

    columns.add(new Column(Identifier.of("product_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("time_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("customer_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_sales_sum", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("store_cost_sum", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("unit_sales_sum", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("fact_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));

    return columns;
  }

  private static List<Column> createAggLl01Columns() {
    List<Column> columns = new ArrayList<>();
    int position = 0;

    columns.add(new Column(Identifier.of("product_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("time_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("customer_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("store_cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("unit_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("fact_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));

    return columns;
  }

  private static List<Column> createAggL03Columns() {
    List<Column> columns = new ArrayList<>();
    int position = 0;

    columns.add(new Column(Identifier.of("time_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("customer_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("store_cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("unit_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("fact_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));

    return columns;
  }

  private static List<Column> createAggL04Columns() {
    List<Column> columns = new ArrayList<>();
    int position = 0;

    columns.add(new Column(Identifier.of("time_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("store_cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("unit_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("customer_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("fact_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));

    return columns;
  }

  private static List<Column> createAggL05Columns() {
    List<Column> columns = new ArrayList<>();
    int position = 0;

    columns.add(new Column(Identifier.of("product_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("customer_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("promotion_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_id", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("store_cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("unit_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("fact_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));

    return columns;
  }

  private static List<Column> createAggC10Columns() {
    List<Column> columns = new ArrayList<>();
    int position = 0;

    columns.add(new Column(Identifier.of("month_of_year", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        false));
    columns.add(new Column(Identifier.of("quarter", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.VARCHAR, 20, 0),
        false));
    columns.add(new Column(Identifier.of("the_year", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.SMALLINT, 0, 0),
        false));
    columns.add(new Column(Identifier.of("store_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("store_cost", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("unit_sales", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.FLOAT, 10, 2),
        false));
    columns.add(new Column(Identifier.of("customer_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));
    columns.add(new Column(Identifier.of("fact_count", new BigQuerySqlDialect()),
        position++,
        DataType.create(SqlTypeName.INTEGER, 0, 0),
        false));

    return columns;
  }
}
