#!/bin/sh
echo "### setup passwords with"
echo "### /opt/janusgraph-1/elasticsearch/bin/elasticsearch-setup-passwords interactive"
echo "###"
# passwd == user
curl -u elastic:elastic --request PUT \
  --header 'Content-Type: application/json' \
  'http://localhost:24499/_security/role/general_read_only' \
  --data '{
    "cluster": [],
    "indices": [
      {
        "names": ["*"],
        "privileges": ["read", "view_index_metadata"]
      }
    ]
  }'
curl -u elastic:elastic --request PUT \
  --header 'Content-Type: application/json' \
  'http://localhost:24499/_security/role/trusted_full_access' \
  --data '{
    "cluster": ["all"],
    "indices": [
      {
        "names": ["*"],
        "privileges": ["all"],
        "allow_restricted_indices": false
      }
    ]
  }'  
