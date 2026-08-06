#!/bin/sh
curl --request PUT \
  --header 'Content-Type: application/json' \
  'http://localhost:24499/_security/role/general_read_only' \
  --data '{
    "cluster": [],
    "indices": [
      {
        "names": ["my-index-*"],
        "privileges": ["read", "view_index_metadata"]
      }
    ]
  }'
curl --request PUT \
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