package com.igot.cb.pores.util;

/**
 * @author Shankaragouda
 */
public class Constants {

    public static final String CORE_CONNECTIONS_PER_HOST_FOR_LOCAL = "coreConnectionsPerHostForLocal";
    public static final String CORE_CONNECTIONS_PER_HOST_FOR_REMOTE = "coreConnectionsPerHostForRemote";
    public static final String HEARTBEAT_INTERVAL = "heartbeatIntervalSeconds";
    public static final String CASSANDRA_CONFIG_HOST = "cassandra.config.host";
    public static final String SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL = "sunbird_cassandra_consistency_level";
    public static final String EXCEPTION_MSG_FETCH = "Exception occurred while fetching record from ";
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String DOT = ".";
    public static final String OPEN_BRACE = "(";
    public static final String VALUES_WITH_BRACE = ") VALUES (";
    public static final String QUE_MARK = "?";
    public static final String COMMA = ",";
    public static final String CLOSING_BRACE = ");";
    public static final String RESPONSE = "response";
    public static final String SUCCESS = "success";
    public static final String FAILED = "Failed";
    public static final String ERROR_MESSAGE = "errmsg";
    public static final String ERROR = "ERROR";
    public static final String KEYWORD = ".keyword";
    public static final String ASC = "asc";
    public static final String DOT_SEPARATOR = ".";
    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String SUB = "sub";
    public static final String SSO_URL = "sso.url";
    public static final String SSO_REALM = "sso.realm";
    public static final String ACCESS_TOKEN_PUBLICKEY_BASEPATH = "accesstoken.publickey.basepath";
    public static final String ID = "id";
    public static final String SEARCH_OPERATION_LESS_THAN = "<";
    public static final String SEARCH_OPERATION_GREATER_THAN = ">";
    public static final String SEARCH_OPERATION_LESS_THAN_EQUALS = "<=";
    public static final String SEARCH_OPERATION_GREATER_THAN_EQUALS = ">=";
    public static final String MUST= "must";
    public static final String FILTER= "filter";
    public static final String MUST_NOT="must_not";
    public static final String SHOULD= "should";
    public static final String BOOL="bool";
    public static final String TERM="term";
    public static final String TERMS="terms";
    public static final String MATCH="match";
    public static final String RANGE="range";
    public static final String UNSUPPORTED_QUERY="Unsupported query type";
    public static final String UNSUPPORTED_RANGE= "Unsupported range condition";
    public static final String UPDATE = "UPDATE ";
    public static final String SET = " SET ";
    public static final String WHERE_ID = "where id";
    public static final String EQUAL_WITH_QUE_MARK = " = ? ";
    public static final String SEMICOLON = ";";
    public static final String USER = "user";
    public static final String UNKNOWN_IDENTIFIER = "Unknown identifier ";
    public static final String EXCEPTION_MSG_UPDATE = "Exception occurred while updating record to ";
    public static final String SEARCH_RESULT_REDIS_TEMPLATE = "searchResultRedisTemplate";
    public static final String REDIS_CONNECTION_FACTORY = "redisConnectionFactory";
    public static final String EXCEPTION_MSG_DELETE = "Exception occurred while deleting record from ";
    public static final String X_AUTH_TOKEN = "x-authenticated-user-token";
    public static final String NUMBER = "number";
    public static final String TYPE = "type";
    public static final String LONG = "long";
    public static final String TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    public static final String INVALID_AUTH_TOKEN = "invalid auth token Please supply a valid auth token";
    public static final String CREATED_BY = "createdBy";
    public static final String CREATED_ON = "createdOn";
    public static final String UPDATED_ON = "updatedOn";
    public static final String IS_ACTIVE = "isActive";
    public static final String IS_MANDATORY = "isMandatory";
    public static final String CUSTOM_FIELD_ID = "customFieldId";
    public static final String API_VERSION_1 = "1.0";
    public static final String UPDATED_BY = "updatedBy";
    public static final String ORGANISATION_ID = "organisationId";
    public static final String CREATE_CUSTOM_FIELD_API = "customFields.create.api";
    public static final String IS_ENABLED = "isEnabled";
    public static final String CUSTOM_FIELD_DATA = "customFieldData";
    public static final String ONLY_EXCEL_FILES_ALLOWED = "Only Excel files (.xlsx, .xls) are allowed";
    public static final String UPLOADED_FILE_IS_EMPTY = "Uploaded file is empty";
    public static final String EXCEL_HEADER_ROW_REQUIRED = "Excel file must have a header row";
    public static final String EXCEL_MORE_COLUMNS_THAN_LEVELS = "Excel file has more columns (%d) than defined levels in customFieldData (%d).";
    public static final String EXCEL_MORE_THAN_MAX_LEVELS = "Excel file cannot have more than %d columns for levels";
    public static final String CUSTOM_FIELD_DATA_LEVELS_EXCEED = "customFieldData levels cannot exceed %d.";
    public static final String CUSTOM_FIELD_DATA_NON_EMPTY = "customFieldData must be a non-empty list";
    public static final String HEADER_MISMATCH = "Header in the excel file '%s' does not match expected attributeName '%s' at level %d";
    public static final String LEVEL_MISMATCH = "Level mismatch at column %d: expected %d, found %d";
    public static final String ERROR_READING_EXCEL = "Error reading Excel file: %s";
    public static final String CUSTOM_FIELD = "CUSTOM_FIELD_";
    public static final String ATTRIBUTE_NAME = "attributeName";
    public static final String LEVEL = "level";
    public static final String INVALID_JSON_CUSTOM_FIELDS_MASTER_DATA = "Invalid JSON for customFieldsMasterData: ";
    public static final String FIELD_NAME = "fieldName";
    public static final String FIELD_VALUE = "fieldValue";
    public static final String FIELD_ATTRIBUTE = "fieldAttribute";
    public static final String PARENT_FIELD_NAME = "parentFieldName";
    public static final String PARENT_FIELD_VALUE = "parentFieldValue";
    public static final String FIELD_VALUES = "fieldValues";
    public static final String SEARCH_RESULTS = "searchResults";
    public static final String CUSTOM_FIELD_ID_PARAM = "customFieldId";
    public static final String STATUS = "status";
    public static final String DELETED = "deleted";
    public static final String REVERSED_ORDER_CUSTOM_FIELD_DATA = "reversedOrderCustomFieldData";

    private Constants() {
    }
}
