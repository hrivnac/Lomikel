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

return [csvDN:          "../data/LightCurves/Latent",
        curvesDN:       "../run/LightCurves",
        sampleSize:      3606, // numbert of LC sample
        jdMinSize:       5,    // minimal number of LC points
        jdSize:          5,    // number of LC points after renormalisation
        miniBatchSize:   64,    // 10, 32, 64
        trainRate:      0.75,
        classes:       [],
        merging:       [:],
        fidValues:      ["1", "2"],
        nEpochs:        100     // 40, 100
        ]
