#!/bin/bash

PRESENT=`grep -c com.Lomikel.GremlinPlugin.LomikelConnector $1`

if [[ "${PRESENT}" == "0" ]]; then
  echo "com.Lomikel.GremlinPlugin.LomikelConnector" >> $1
  fi
