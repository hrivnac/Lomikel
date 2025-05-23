import com.Lomikel.HBaser.AsynchHBaseClient;

client = new AsynchHBaseClient("vdhbase1.lal.in2p3.fr", 2183);
client.connect("ztf", "schema_3.1_5.6.2");

client.setLimit(10000);
client.setMaxQueueSize(1000);
client.setLoopWait(1);
client.startScan(null,
                 null,
                 "i:ra,i:dec",
                 0,
                 0,
                 true,
                 true);
while (client.scanning() || client.size() > 0) {
  if (client.size() > 0) {
    println(client.size() + ":\t" + client.poll());
    }
  }
//client.stop(false);

client.setLimit(1000);
client.setLoopWait(500);
client.scan(null,
            null,
            "i:ra,i:dec",
            0,
            0,
            true,
            true,
            10);

client.close();
