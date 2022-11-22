cd ../ant
source setup.sh
gremlin_console_Local < ../src/gremlin/schema.gremlin
gremlin_console_Local < ../src/gremlin/indexHB.gremlin
gremlin_console_Local < ../src/gremlin/indexES.gremlin
gremlin_console_Local < ../src/gremlin/testGraph.gremlin
