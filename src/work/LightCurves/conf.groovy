allFinkClasses = ["(CTA) Blazar",
                  "Ambiguous",
                  "Early SN Ia candidate",
                  "Kilonova candidate",
                  "Microlensing candidate",
                  "SN candidate",
                  "Solar System candidate",
                  "Solar System MPC",
                  "Tracklet"]
CQSOClasses    = ["C*", "QSO"];      

return [csvDN:          "../data/LightCurves",
        curvesDN:       "../run/LightCurves",
        jdMinSize:       100,    // minimal number of LC points
        jdSize:          100,    // number of LC points after renormalisation
        normalize:       true,  // normalize data or fill missing with 0s
        reduce:          true, // merge some classes
        miniBatchSize:   10,    // 10, 32, 64
        blockSize:      100,    // number of LC samples (smaler cases will be skipped, larger cases will be shortened)
        trainRate:      0.75,
        trainClasses:   ["LPV*", "Mira", "RRLyr"],
        trainFid:       1,
        nEpochs:        40     // 40, 100
        ]
