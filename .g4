grammar BigQuerySql;

@header {
package com.truflow.sql.parser.antlr;
}

statement
    : dmlStatement
    | ddlStatement
    ;

ddlStatement
    : createTableStatement
    | createViewStatement
    ;

dmlStatement
    : selectStatement
    | insertStatement
    | deleteStatement
    | updateStatement
    | mergeStatement
    ;

createViewStatement
    : CREATE (OR REPLACE)? (TEMPORARY | TEMP)? VIEW
      (IF NOT EXISTS)? tableIdentifier
      (
          (LPAREN columnName (',' columnName)* RPAREN)?
          AS queryExpression
          (OPTIONS LPAREN viewOptionList RPAREN)?
      )
    ;

viewOptionList
    : viewOption (',' viewOption)*
    ;

viewOption
    : optionName '=' optionValue
    ;

createTableStatement
    : CREATE (OR REPLACE)? (TEMPORARY | TEMP)? TABLE
      (IF NOT EXISTS)? tableIdentifier
      (
          LIKE sourceTable=tableIdentifier
          | COPY sourceTable=tableIdentifier
          | AS queryExpression
          | CLONE sourceTable=tableIdentifier
          | LPAREN tableElementList RPAREN
            (PARTITION BY partitionExpression)?
            (CLUSTER BY clusteringColumnList)?
            (OPTIONS LPAREN tableOptionList RPAREN)?
      )
    ;

tableElementList
    : tableElement (',' tableElement)*
    ;

tableElement
    : columnDefinition
    | tableConstraint
    ;

columnDefinition
    : columnName dataType
      (DEFAULT defaultExpression)?
      (NOT NULL)?
      (HIDDEN_COLUMN)?
      (PRIMARY KEY)?
      (REFERENCES tableIdentifier (LPAREN columnName RPAREN)? (ON DELETE referentialAction)? (ON UPDATE referentialAction)?)?
      (OPTIONS LPAREN columnOptionList RPAREN)?
      (AS LPAREN generationExpression RPAREN)?
      (GENERATED ALWAYS AS LPAREN generationExpression RPAREN (STORED | VIRTUAL)?)?
    ;

tableConstraint
    : (CONSTRAINT constraintName)?
      (
          PRIMARY KEY LPAREN columnName (',' columnName)* RPAREN
          | FOREIGN KEY LPAREN columnName (',' columnName)* RPAREN
            REFERENCES tableIdentifier (LPAREN columnName (',' columnName)* RPAREN)?
            (ON DELETE referentialAction)?
            (ON UPDATE referentialAction)?
      )
    ;

referentialAction
    : RESTRICT
    | CASCADE
    | SET NULL
    | NO ACTION
    ;

partitionExpression
    : columnName
    | DATE_TRUNC LPAREN columnName ',' dateUnit RPAREN
    | TIMESTAMP_TRUNC LPAREN columnName ',' dateUnit RPAREN
    | RANGE_BUCKET LPAREN columnName ',' GENERATE_ARRAY LPAREN startExpr ',' endExpr ',' stepExpr RPAREN RPAREN
    | RANGE LPAREN startExpr ',' endExpr (',' stepExpr)? RPAREN
    | expression
    ;

clusteringColumnList
    : columnName (',' columnName)*
    ;

tableOptionList
    : tableOption (',' tableOption)*
    ;

tableOption
    : optionName '=' optionValue
    ;

columnOptionList
    : columnOption (',' columnOption)*
    ;

columnOption
    : optionName '=' optionValue
    ;

optionName
    : identifier (DOT identifier)*
    ;

optionValue
    : literal
    | identifier
    ;

defaultExpression
    : expression
    ;

generationExpression
    : expression
    ;

startExpr
    : expression
    ;

endExpr
    : expression
    ;

stepExpr
    : expression
    ;

columnName
    : identifier
    ;

constraintName
    : identifier
    ;

insertStatement
    : INSERT (INTO)? tableIdentifier (AS? alias=identifier)?
      (LPAREN columnReference (',' columnReference)* RPAREN)?
      (
          VALUES valueRow (',' valueRow)*
          | selectStatement
          | ROW (LPAREN expression (',' expression)* RPAREN)?
      )
    ;

valueRow
    : LPAREN expression (',' expression)* RPAREN
    ;

deleteStatement
    : DELETE (FROM)? tableFactor
      WHERE whereCondition=expression
    ;

updateStatement
    : UPDATE tableIdentifier (AS? alias=identifier)?
      SET updateSetItem (',' updateSetItem)*
      (FROM tableExpression (',' tableExpression)*)?
      (WHERE whereCondition=expression)?
    ;

updateSetItem
    : primaryExpression EQ expression
    | LPAREN columnReference (',' columnReference)* RPAREN '='
      LPAREN expression (',' expression)* RPAREN
    | LPAREN columnReference (',' columnReference)* RPAREN '='
      LPAREN queryExpression RPAREN
    | columnReference '=' DEFAULT
    ;

mergeStatement
    : MERGE INTO tableIdentifier (AS? targetAlias=tableAlias)? USING tableExpression (AS? sourceAlias=tableAlias)? ON expression
      mergeWhenClause+
    ;

mergeWhenClause
    : WHEN MATCHED (AND condition=expression)? THEN mergeMatchedAction
    | WHEN NOT MATCHED (AND condition=expression)? THEN mergeNotMatchedByTargetAction
    ;

mergeMatchedAction
    : UPDATE SET updateSetItem (',' updateSetItem)*
    | DELETE
    ;

mergeNotMatchedByTargetAction
    : INSERT ('(' columnName (',' columnName)* ')')? VALUES '(' expression (',' expression)* ')'
    ;

mergeNotMatchedBySourceAction
    : DELETE
    ;

selectStatement
    : withClause? queryExpression orderByClause? limitClause?
    ;

withClause
    : WITH withQueryItem (',' withQueryItem)*
    ;

withQueryItem
    : identifier AS LPAREN queryExpression RPAREN
    ;

queryExpression
    : queryTerm
    | queryExpression UNION (ALL | DISTINCT)? queryTerm
    | queryExpression EXCEPT (ALL | DISTINCT)? queryTerm
    | queryExpression INTERSECT (ALL | DISTINCT)? queryTerm
    ;

queryTerm
    : queryPrimary
    ;

queryPrimary
    : simpleQuery
    | LPAREN queryExpression RPAREN
    ;

simpleQuery
    : selectClause
      fromClause?
      whereClause?
      groupByClause?
      havingClause?
      qualifyClause?
      windowClause?
      orderByClause?
      limitClause?
    ;

selectClause
    : SELECT (AS STRUCT)? setQuantifier? selectItem (',' selectItem)*
    ;

selectItem
    : expression (AS? identifier)?
    | STAR
    | tableWildcard
    ;

tableWildcard
    : tableIdentifier DOT STAR
    ;

fromClause
    : FROM tableExpression (',' tableExpression)*
    ;

tableExpression
    : /*tableFactor
    | */joinedTable
    ;

tableFactor
    : tableIdentifier tableAlias?
    | LPAREN queryExpression RPAREN tableAlias?
    | LPAREN tableExpression RPAREN tableAlias?
    | UNNEST LPAREN expression RPAREN tableAlias? (WITH OFFSET (AS? identifier)?)?
    | functionCall tableAlias?
    ;

tableAlias
    : AS? identifier columnAlias?
    ;

columnAlias
    : LPAREN identifier (',' identifier)* RPAREN
    ;

joinedTable
    : tableFactor
    | joinedTable joinType? JOIN tableExpression joinSpecification?
    ;

joinType
    : INNER
    | CROSS
    | FULL (OUTER)?
    | LEFT (OUTER)?
    | RIGHT (OUTER)?
    ;

joinSpecification
    : ON expression
    | USING LPAREN identifier (',' identifier)* RPAREN
    ;

whereClause
    : WHERE expression
    ;

groupByClause
    : GROUP BY setQuantifier? groupingElement (',' groupingElement)*
    ;

groupingElement
    : expression
    | ROLLUP LPAREN expression (',' expression)* RPAREN
    | CUBE LPAREN expression (',' expression)* RPAREN
    | GROUPING SETS LPAREN groupingSet (',' groupingSet)* RPAREN
    ;

groupingSet
    : LPAREN (expression (',' expression)*)? RPAREN
    ;

havingClause
    : HAVING expression
    ;

qualifyClause
    : QUALIFY expression
    ;

windowClause
    : WINDOW namedWindow (',' namedWindow)*
    ;

namedWindow
    : identifier AS windowSpecification
    ;

windowSpecification
    : LPAREN
      windowName?
      partitionClause?
      orderByClause?
      frameClause?
      RPAREN
    ;

windowName
    : identifier
    ;

partitionClause
    : PARTITION BY expression (',' expression)*
    ;

orderByClause
    : ORDER BY orderingItem (',' orderingItem)*
    ;

orderingItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

frameClause
    : frameUnits frameExtent
    ;

frameUnits
    : ROWS
    | RANGE
    ;

frameExtent
    : frameStart
    | BETWEEN frameStart AND frameEnd
    ;

frameStart
    : UNBOUNDED PRECEDING
    | expression PRECEDING
    | CURRENT ROW
    | expression FOLLOWING
    | UNBOUNDED FOLLOWING
    ;

frameEnd
    : UNBOUNDED PRECEDING
    | expression PRECEDING
    | CURRENT ROW
    | expression FOLLOWING
    | UNBOUNDED FOLLOWING
    ;

limitClause
    : LIMIT expression
    ;

setQuantifier
    : ALL
    | DISTINCT
    ;

// Expression syntax
expression
    : logicalOrExpression
    ;

// Logical OR has the lowest precedence
logicalOrExpression
    : logicalAndExpression (OR logicalAndExpression)*
    ;

logicalAndExpression
    : equalityExpression (AND equalityExpression)*
    ;

equalityExpression
    : comparisonExpression ((EQ | NEQ | NE) comparisonExpression)*
    ;

comparisonExpression
    : rangeExpression ((LT | GT | LTE | GTE) rangeExpression)*
    ;

rangeExpression
    : additiveExpression (BETWEEN additiveExpression AND additiveExpression)?
    | additiveExpression (NOT? IN inExpressionList)?
    | additiveExpression (NOT? LIKE additiveExpression (ESCAPE additiveExpression)?)?
    ;

inExpressionList
    : LPAREN queryExpression RPAREN
    | LPAREN (expression (',' expression)*)? RPAREN
    | UNNEST LPAREN expression RPAREN
    ;

additiveExpression
    : multiplicativeExpression ((PLUS | MINUS | CONCAT) multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression ((STAR | DIVIDE | DIV | MOD) unaryExpression)*
    ;

unaryExpression
    : (NOT | PLUS | MINUS) unaryExpression
    | isExpression
    ;

isExpression
    : primaryExpression (IS NOT? (NULL | NAN | TRUE | FALSE))?
    ;

primaryExpression
    : literalValue                                                                  # literalPE
    | parameterReference                                                            # parameterPE
    | caseExpression                                                                # casePE
    | castExpression                                                                # castPE
    | extractExpression                                                             # extractPE
    | primaryExpression LBRACKET OFFSET LPAREN expression RPAREN RBRACKET           # arrayOffsetPE
    | functionCall                                                                  # functionCallPE
    | columnReference                                                               # columnReferencePE
    | arrayExpression                                                               # arrayPE
    | structExpression                                                              # structPE
    | primaryExpression SAFE? FLOAT_LITERAL                                         # structOrdinalAccessPE
    | primaryExpression (SAFE? DOT) identifier                                      # simpleStructAccessPE
    | queryExpression                                                               # queryPE
    | ifExpression                                                                  # ifPE
    | primaryExpression LBRACKET expression RBRACKET                                # arrayElementAccess
    | primaryExpression LBRACKET expression ':' expression RBRACKET                 # arraySlice
    | primaryExpression LBRACKET SAFE_OFFSET LPAREN expression RPAREN RBRACKET      # arraySafeOffset
    | primaryExpression LBRACKET ORDINAL LPAREN expression RPAREN RBRACKET          # arrayOrdinal
    | primaryExpression LBRACKET SAFE_ORDINAL LPAREN expression RPAREN RBRACKET     # arraySafeOrdinal
//    | primaryExpression DOT identifier
    | LPAREN expression RPAREN                                                      # parenthesisedPE
    ;

parameterReference
    : '@' identifier
    ;

ifExpression
    : IF LPAREN expression ',' expression ',' expression RPAREN
    ;

extractExpression
    : EXTRACT LPAREN extractField FROM expression RPAREN
    ;

extractField
    : MICROSECOND
    | MILLISECOND
    | SECOND
    | MINUTE
    | HOUR
    | DAY
    | DAYOFWEEK
    | DAYOFYEAR
    | WEEK
    | MONTH
    | QUARTER
    | YEAR
    | DATE
    | TIME
    | DATETIME
    | identifier
    ;

//subquery
//    : LPAREN SELECT expression RPAREN
//    ;

literalValue
    : NUMERIC_LITERAL      // Numbers (integer, float)
    | STRING_LITERAL       // String literals
    | BYTES_LITERAL        // Bytes literals
    | DATE_LITERAL         // Date literals
    | TIMESTAMP_LITERAL    // Timestamp literals
    | BOOL_LITERAL         // Boolean literals (true/false)
    | NULL                 // NULL value
    ;

caseExpression
    : simpleCaseExpression
    | searchedCaseExpression
    ;

simpleCaseExpression
    : CASE expression (WHEN expression THEN expression)+ (ELSE expression)? END
    ;

searchedCaseExpression
    : CASE (WHEN expression THEN expression)+ (ELSE expression)? END
    ;

castExpression
    : CAST LPAREN expression AS dataType RPAREN
    | SAFE_CAST LPAREN expression AS dataType RPAREN
    ;

arrayExpression
    : LBRACKET (expression (',' expression)*)? RBRACKET
    | ARRAY '<' dataType '>' LBRACKET (expression (',' expression)*)? RBRACKET
//    | ARRAY LPAREN (SELECT selectItem FROM tableExpression whereClause?) RPAREN
    ;

structExpression
    : STRUCT (LPAREN structField (',' structField)* RPAREN)?
    | STRUCT '<' identifier dataType (',' identifier dataType)* '>'
    ;

structField
    : expression (AS identifier)?
    ;

functionCall
    : (COUNT | SUM | AVG | MIN | MAX) LPAREN (DISTINCT | ALL)? (expression | '*') RPAREN
    | identifier LPAREN '*' RPAREN
    | EXTRACT LPAREN identifier FROM expression RPAREN
    | DATE_ADD LPAREN expression ',' INTERVAL expression dateUnit RPAREN
    | DATE_SUB LPAREN expression ',' INTERVAL expression dateUnit RPAREN
    | DATE_DIFF LPAREN expression ',' expression ',' dateUnit RPAREN
    | TIMESTAMP_ADD LPAREN expression ',' INTERVAL expression dateUnit RPAREN
    | TIMESTAMP_SUB LPAREN expression ',' INTERVAL expression dateUnit RPAREN
    | TIMESTAMP_DIFF LPAREN expression ',' expression ',' dateUnit RPAREN
    | DATE_TRUNC LPAREN expression ',' dateUnit RPAREN
    | TIMESTAMP_TRUNC LPAREN expression ',' dateUnit RPAREN
    | JSON_EXTRACT LPAREN expression ',' expression RPAREN
    | JSON_EXTRACT_SCALAR LPAREN expression ',' expression RPAREN
    | JSON_QUERY LPAREN expression ',' expression RPAREN
    | JSON_VALUE LPAREN expression ',' expression RPAREN
    | TO_JSON_STRING LPAREN expression RPAREN
    | PARSE_JSON LPAREN expression RPAREN
    | ARRAY_AGG LPAREN (DISTINCT | ALL)? expression (ORDER BY orderingItem (',' orderingItem)*)? (LIMIT expression)? RPAREN
    | GENERATE_ARRAY LPAREN expression ',' expression (',' expression)? RPAREN
    | GENERATE_DATE_ARRAY LPAREN expression ',' expression (',' expression)? RPAREN
    | (identifier LPAREN (DISTINCT | ALL)? (expression (',' expression)*)? RPAREN | (ROW_NUMBER | RANK) LPAREN RPAREN) (OVER windowSpecification)?
    ;

comparisonOperator
    : '='
    | '<'
    | '>'
    | '<='
    | '>='
    | '<>'
    | '!='
    ;

// It could refer nested struct field as well
//columnReference
//    : identifier
//    | tableIdentifier DOT identifier
//    | tableIdentifier DOT tableIdentifier DOT identifier
//    ;

columnReference
    : identifier (DOT identifier)*                                           //#simpleColumnReference
    ;

tableIdentifier
    : hyphenatedIdentifier (DOT hyphenatedIdentifier)*
    ;

hyphenatedIdentifier
    : IDENTIFIER (MINUS IDENTIFIER)*
    | BACKTICK_IDENTIFIER
    | QUOTED_IDENTIFIER
    ;

dataType
    : INT64
    | FLOAT64
    | NUMERIC
    | BIGNUMERIC
    | BOOL
    | BOOLEAN
    | STRING (LPAREN INTEGER_LITERAL RPAREN)?
    | BYTES (LPAREN INTEGER_LITERAL RPAREN)?
    | DATE
    | DATETIME
    | TIME
    | TIMESTAMP
    | INTERVAL
    | GEOGRAPHY
    | JSON
    | ARRAY '<' dataType '>'
    | STRUCT '<' structTypeElement (',' structTypeElement)* '>'
    ;

structTypeElement
    : identifier dataType
    ;

dateUnit
    : YEAR | QUARTER | MONTH | WEEK | DAY | HOUR | MINUTE | SECOND | MILLISECOND | MICROSECOND | NANOSECOND
    ;

literal
    : STRING_LITERAL
    | INTEGER_LITERAL
    | FLOAT_LITERAL
    | TRUE
    | FALSE
    | NULL
    | DATE STRING_LITERAL
    | TIME STRING_LITERAL
    | TIMESTAMP STRING_LITERAL
    | DATETIME STRING_LITERAL
    ;

identifier
    : IDENTIFIER
    | BACKTICK_IDENTIFIER
    | QUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

nonReservedKeyword
    : ALL
    | AND
    | ANY
    | ARRAY
    | AS
    | ASC
    | BETWEEN
    | BY
    | CASE
    | CAST
    | COLLATE
    | CROSS
    | CURRENT
    | DAY
    | DATE
    | DESC
    | DISTINCT
    | ELSE
    | END
    | EXISTS
    | EXTRACT
    | FALSE
    | FIRST
    | FOLLOWING
    | FROM
    | FULL
    | GROUP
    | GROUPING
    | HAVING
    | IN
    | INNER
    | INTERSECT
    | IS
    | JOIN
    | LAST
    | LEFT
    | LIKE
    | LIMIT
    | MONTH
    | MOD
    | NOT
    | NULL
    | NULLS
    | OFFSET
    | ON
    | OR
    | ORDER
    | OUTER
    | OVER
    | PARTITION
    | PRECEDING
    | QUALIFY
    | RANGE
    | REPLACE
    | RIGHT
    | ROLLUP
    | ROW
    | ROWS
    | SELECT
    | SETS
    | SOME
    | STRUCT
    | TIMESTAMP
    | TRUE
    | UNBOUNDED
    | UNION
    | USING
    | WHEN
    | WHERE
    | WINDOW
    | WITH
    | YEAR
    | QUARTER
    ;

// Lexer Rules
ARRAY_AGG               : A R R A Y [_] A G G ;
ALL                     : A L L ;
AND                     : A N D ;
ANY                     : A N Y ;
ARRAY                   : A R R A Y ;
AS                      : A S ;
INTO                   : I N T O ;
SET                    : S E T ;
ASC                     : A S C ;
AVG                     : A V G ;
BETWEEN                 : B E T W E E N ;
BY                      : B Y ;
CASE                    : C A S E ;
CAST                    : C A S T ;
COLLATE                 : C O L L A T E ;
COUNT                   : C O U N T ;
CROSS                   : C R O S S ;
CUBE                    : C U B E ;
CURRENT                 : C U R R E N T ;
DATE                    : D A T E ;
DATE_ADD                : D A T E [_] A D D ;
DATE_DIFF               : D A T E [_] D I F F ;
DATE_SUB                : D A T E [_] S U B ;
DATE_TRUNC              : D A T E [_] T R U N C ;
DATETIME                : D A T E T I M E ;
TIMESTAMP_ADD           : T I M E S T A M P [_] A D D ;
TIMESTAMP_SUB           : T I M E S T A M P [_] S U B ;
TIMESTAMP_DIFF          : T I M E S T A M P [_] D I F F ;
TIMESTAMP_TRUNC         : T I M E S T A M P [_] T R U N C ;
TO_JSON_STRING          : T O [_] J S O N [_] S T R I N G ;
TIME                    : T I M E ;
TIMESTAMP               : T I M E S T A M P ;
DESC                    : D E S C ;
DISTINCT                : D I S T I N C T ;
ELSE                    : E L S E ;
END                     : E N D ;
EXCEPT                  : E X C E P T ;
EXISTS                  : E X I S T S ;
EXTRACT                 : E X T R A C T ;
FIRST                   : F I R S T ;
FOLLOWING               : F O L L O W I N G ;
CREATE                  : C R E A T E ;
REPLACE                 : R E P L A C E ;
TEMPORARY               : T E M P O R A R Y ;
TEMP                    : T E M P ;
TABLE                   : T A B L E ;
VIEW                    : V I E W ;
IF                      : I F ;
COPY                    : C O P Y ;
CLONE                   : C L O N E ;
CLUSTER                 : C L U S T E R ;
OPTIONS                 : O P T I O N S ;
INSERT                  : I N S E R T ;
DELETE                  : D E L E T E ;
UPDATE                  : U P D A T E ;
MERGE                   : M E R G E ;
FROM                    : F R O M ;
FULL                    : F U L L ;
GENERATE_ARRAY          : G E N E R A T E [_] A R R A Y ;
GENERATE_DATE_ARRAY     : G E N E R A T E [_] D A T E [_] A R R A Y ;
GROUP                   : G R O U P ;
GROUPING                : G R O U P I N G ;
HAVING                  : H A V I N G ;
IN                      : I N ;
INNER                   : I N N E R ;
INTERSECT               : I N T E R S E C T ;
INTERVAL                : I N T E R V A L ;
IS                      : I S ;
JOIN                    : J O I N ;
JSON_EXTRACT            : J S O N [_] E X T R A C T ;
JSON_EXTRACT_SCALAR     : J S O N [_] E X T R A C T [_] S C A L A R ;
JSON_QUERY              : J S O N [_] Q U E R Y ;
JSON_VALUE              : J S O N [_] V A L U E ;
LAST                    : L A S T ;
LEFT                    : L E F T ;
LIKE                    : L I K E ;
ESCAPE                 : E S C A P E ;
LIMIT                   : L I M I T ;
MAX                     : M A X ;
ROW_NUMBER              : R O W [_] N U M B E R ;
RANK                    : R A N K ;
MIN                     : M I N ;
NOT                     : N O T ;
HIDDEN_COLUMN           : H I D D E N ;
PRIMARY                 : P R I M A R Y ;
FOREIGN                 : F O R E I G N ;
RESTRICT                : R E S T R I C T ;
CASCADE                 : C A S C A D E ;
ACTION                  : A C T I O N ;
RANGE_BUCKET           : R A N G E [_] B U C K E T ;
NO                      : N O ;
KEY                     : K E Y ;
REFERENCES              : R E F E R E N C E S ;
GENERATED               : G E N E R A T E D ;
ALWAYS                  : A L W A Y S ;
STORED                  : S T O R E D ;
VIRTUAL                 : V I R T U A L ;
CONSTRAINT              : C O N S T R A I N T ;
NULLS                   : N U L L S ;
OFFSET                  : O F F S E T ;
SAFE                    : S A F E ;
SAFE_OFFSET             : S A F E [_] O F F S E T ;
ORDINAL                 : O R D I N A L ;
SAFE_ORDINAL            : S A F E [_] O R D I N A L ;
ON                      : O N ;
OR                      : O R ;
ORDER                   : O R D E R ;
OUTER                   : O U T E R ;
OVER                    : O V E R ;
PARSE_JSON              : P A R S E [_] J S O N ;
PARTITION               : P A R T I T I O N ;
PRECEDING               : P R E C E D I N G ;
QUALIFY                 : Q U A L I F Y ;
RANGE                   : R A N G E ;
RIGHT                   : R I G H T ;
ROLLUP                  : R O L L U P ;
VALUES                  : V A L U E S ;
ROW                     : R O W ;
ROWS                    : R O W S ;
SAFE_CAST               : S A F E [_] C A S T ;
SELECT                  : S E L E C T ;
STRING                  : S T R I N G ;
BYTES                   : B Y T E S ;
BOOL                    : B O O L ;
BOOLEAN                 : B O O L E A N ;
NUMERIC                 : N U M E R I C ;
BIGNUMERIC              : B I G N U M E R I C ;
GEOGRAPHY               : G E O G R A P H Y ;
DEFAULT                 : D E F A U L T ;
YEAR         : Y E A R ;
QUARTER      : Q U A R T E R ;
MONTH        : M O N T H ;
WEEK         : W E E K ;
DAY          : D A Y ;
DAYOFWEEK       : D A Y O F W E E K ;
DAYOFYEAR       : D A Y O F Y E A R ;
HOUR         : H O U R ;
MINUTE       : M I N U T E ;
SECOND       : S E C O N D ;
MILLISECOND  : M I L L I S E C O N D ;
MICROSECOND  : M I C R O S E C O N D ;
NANOSECOND   : N A N O S E C O N D ;
TRUE  : T R U E ;
NAN   : N A N ;
FALSE : F A L S E ;
NULL  : N U L L ;
WITH                    : W I T H ;
UNION                   : U N I O N ;
UNNEST                  : U N N E S T ;
USING                   : U S I N G ;
WHERE                   : W H E R E ;
SETS                    : S E T S ;
WINDOW                  : W I N D O W ;
UNBOUNDED               : U N B O U N D E D ;
WHEN                    : W H E N ;
MATCHED                 : M A T C H E D ;
//SOURCE                  : S O U R C E ;
//TARGET                  : T A R G E T ;
THEN                    : T H E N ;
STRUCT                  : S T R U C T ;
SUM                     : S U M ;
SOME                    : S O M E ;
INT64                   : I N T '64' ;
FLOAT64                 : F L O A T '64' ;
JSON                    : J S O N ;
LPAREN                  : '(' ;
RPAREN                  : ')' ;
LBRACKET                : '[' ;
RBRACKET                : ']' ;
EQ                      : '=';
NEQ                     : '!=';
NE                      : '<>';
LT                      : '<';
GT                      : '>';
LTE                     : '<=';
GTE                     : '>=';
PLUS                    : '+';
MINUS                   : '-';
STAR                    : '*';
DIVIDE                  : '/';
DIV                     : D I V;
MOD                     : M O D ;
CONCAT                  : '||';
DOT                     : '.';


IDENTIFIER
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

BACKTICK_IDENTIFIER
    : '`' ( ~'`' | '``' )* '`'
    ;

QUOTED_IDENTIFIER
    : '"' ( ~'"' | '""' )* '"'
    ;

NUMERIC_LITERAL
    : DIGIT+ (DOT DIGIT*)? (('E'|'e') ('+'|'-')? DIGIT+)?
    ;

STRING_LITERAL
    : '\'' ( ~'\'' | '\'\'' )* '\''
    ;

BYTES_LITERAL
    : 'b' STRING_LITERAL
    | 'rb' STRING_LITERAL
    ;

DATE_LITERAL
    : 'DATE' STRING_LITERAL
    ;

TIMESTAMP_LITERAL
    : 'TIMESTAMP' STRING_LITERAL
    ;

BOOL_LITERAL
    : TRUE | FALSE
    ;

INTEGER_LITERAL
    : DIGIT+
    ;

FLOAT_LITERAL
    : DIGIT+ DOT DIGIT* EXPONENT?
    | DOT DIGIT+ EXPONENT?
    | DIGIT+ EXPONENT
    ;

fragment DIGIT
    : [0-9]
    ;

fragment EXPONENT
    : E [+\-]? DIGIT+
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

COMMENT
    : '--' ~[\r\n]* -> skip
    ;

MULTILINE_COMMENT
    : '/*' .*? '*/' -> skip
    ;


// create fragments of all the letters like A : A ;
fragment
A : [Aa] ;
B : [Bb] ;
C : [Cc] ;
D : [Dd] ;
E : [Ee] ;
F : [Ff] ;
G : [Gg] ;
H : [Hh] ;
I : [Ii] ;
J : [Jj] ;
K : [Kk] ;
L : [Ll] ;
M : [Mm] ;
N : [Nn] ;
O : [Oo] ;
P : [Pp] ;
Q : [Qq] ;
R : [Rr] ;
S : [Ss] ;
T : [Tt] ;
U : [Uu] ;
V : [Vv] ;
W : [Ww] ;
X : [Xx] ;
Y : [Yy] ;
Z : [Zz] ;
