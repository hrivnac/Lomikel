cd ../ant
source setup.sh
gremlin_console < ../src/gremlin/schema.gremlin
gremlin_console < ../src/gremlin/indexHB.gremlin
gremlin_console < ../src/gremlin/indexES.gremlin
gremlin_console < ../src/gremlin/testGraph.gremlin
