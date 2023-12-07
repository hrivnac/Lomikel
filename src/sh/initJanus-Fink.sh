cd ../ant
source setup.sh
gremlin_console_Local < ../src/gremlin/schema-Fink.gremlin
gremlin_console_Local < ../src/gremlin/indexHB-Fink.gremlin
gremlin_console_Local < ../src/gremlin/indexES-Fink.gremlin
