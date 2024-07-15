# read light curve data from all.lst file
# and train them using UCISequenceClassification

import com.Krakev.deeplearning4j.UCISequenceClassification.UCISequenceClassification;

classifier = new UCISequenceClassification();

classifier.setUpStorage("../data/lc/");
data = new File("all.lst").text;
classifier.prepareData(data);
classifier.train();

