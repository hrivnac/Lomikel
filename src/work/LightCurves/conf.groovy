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

return [csvDN:          "../data/LightCurves/2024",
        curvesDN:       "../run/LightCurves",
        jdMinSize:       10,    // minimal number of LC points
        jdSize:          50,    // number of LC points after renormalisation
        normalize:       true,  // normalize data or fill missing with 0s
        reduce:          true, // merge some classes
        miniBatchSize:   64,    // 10, 32, 64
        blockSize:      100,    // number of LC samples (smaler cases will be skipped, larger cases will be shortened)
        trainRate:      0.75,
        trainClasses:   ["Mira", "LPV*"],
        fidValues:      ["1", "2"],
        trainFid:       2,
        nEpochs:        100     // 40, 100
        ]
