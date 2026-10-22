#!/bin/bash
set -e

ENDPOINT="http://localhost:4566"
REGION="eu-central-1"

echo "Creating 'Commerce' table via Floci in $REGION..."

curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/x-amz-json-1.0" \
  -H "X-Amz-Target: DynamoDB_20120810.CreateTable" \
  -H "Authorization: AWS4-HMAC-SHA256 Credential=test/$(date -u +%Y%m%d)/$REGION/dynamodb/aws4_request" \
  -d '{
    "TableName": "Commerce",
    "AttributeDefinitions": [
      {"AttributeName": "pk",     "AttributeType": "S"},
      {"AttributeName": "sk",     "AttributeType": "S"},
      {"AttributeName": "gsi1pk", "AttributeType": "S"},
      {"AttributeName": "gsi1sk", "AttributeType": "S"},
      {"AttributeName": "gsi2pk", "AttributeType": "S"},
      {"AttributeName": "gsi2sk", "AttributeType": "S"}
    ],
    "KeySchema": [
      {"AttributeName": "pk", "KeyType": "HASH"},
      {"AttributeName": "sk", "KeyType": "RANGE"}
    ],
    "GlobalSecondaryIndexes": [
      {
        "IndexName": "GSI1",
        "KeySchema": [
          {"AttributeName": "gsi1pk", "KeyType": "HASH"},
          {"AttributeName": "gsi1sk", "KeyType": "RANGE"}
        ],
        "Projection": {"ProjectionType": "ALL"},
        "ProvisionedThroughput": {"ReadCapacityUnits": 5, "WriteCapacityUnits": 5}
      },
      {
        "IndexName": "GSI2",
        "KeySchema": [
          {"AttributeName": "gsi2pk", "KeyType": "HASH"},
          {"AttributeName": "gsi2sk", "KeyType": "RANGE"}
        ],
        "Projection": {"ProjectionType": "ALL"},
        "ProvisionedThroughput": {"ReadCapacityUnits": 5, "WriteCapacityUnits": 5}
      }
    ],
    "ProvisionedThroughput": {
      "ReadCapacityUnits": 5,
      "WriteCapacityUnits": 5
    }
  }'

echo ""
echo "Creating on-demand 'Products' table with vector index 'ProductEmbeddingIndex' in $REGION..."

curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/x-amz-json-1.0" \
  -H "X-Amz-Target: DynamoDB_20120810.CreateTable" \
  -H "Authorization: AWS4-HMAC-SHA256 Credential=test/$(date -u +%Y%m%d)/$REGION/dynamodb/aws4_request" \
  -d '{
    "TableName": "Products",
    "BillingMode": "PAY_PER_REQUEST",
    "AttributeDefinitions": [
      {"AttributeName": "productId", "AttributeType": "S"},
      {"AttributeName": "category",  "AttributeType": "S"},
      {"AttributeName": "brand",     "AttributeType": "S"}
    ],
    "KeySchema": [
      {"AttributeName": "productId", "KeyType": "HASH"}
    ],
    "VectorIndexes": [
      {
        "IndexName": "ProductEmbeddingIndex",
        "VectorAttribute": {"AttributeName": "embedding"},
        "Dimensions": 3,
        "DistanceFunction": "COSINE",
        "Projection": {"ProjectionType": "ALL"},
        "SearchSchema": [
          {"AttributeName": "category", "SearchSchemaElementType": "INLINE_FILTER"},
          {"AttributeName": "brand",    "SearchSchemaElementType": "INLINE_FILTER"}
        ]
      }
    ]
  }'

echo ""
echo "Done! Verify tables in $REGION:"

curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/x-amz-json-1.0" \
  -H "X-Amz-Target: DynamoDB_20120810.ListTables" \
  -d '{}'

echo ""
