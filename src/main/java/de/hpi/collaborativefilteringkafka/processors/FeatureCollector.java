package de.hpi.collaborativefilteringkafka.processors;

import de.hpi.collaborativefilteringkafka.apps.ALSApp;
import de.hpi.collaborativefilteringkafka.messages.FeatureMessage;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.PunctuationType;
import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_FDRM;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;

public class FeatureCollector extends AbstractProcessor<Integer, FeatureMessage> {
    private ProcessorContext context;

    // TODO: use TreeMap instead to avoid race conditions? can we be sure of the order of the feature vectors?
    private HashMap<Integer, ArrayList<Float>> mFeaturesMap;
    private HashMap<Integer, ArrayList<Float>> uFeaturesMap;
    private int mostRecentMFeaturesMapSize;
    private int mostRecentUFeaturesMapSize;
    private boolean hasPredictionMatrixBeenComputed;

    private FMatrixRMaj mFeaturesMatrix;
    private FMatrixRMaj uFeaturesMatrix;

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.context = context;

        this.mFeaturesMap = new HashMap<>();
        this.uFeaturesMap = new HashMap<>();
        this.mostRecentMFeaturesMapSize = this.mFeaturesMap.size();
        this.mostRecentUFeaturesMapSize = this.uFeaturesMap.size();
        this.hasPredictionMatrixBeenComputed = false;

        // TODO: optimize wait time?
        this.context.schedule(Duration.ofSeconds(3), PunctuationType.WALL_CLOCK_TIME, timestamp -> {
            // if there are no final feature vectors yet, just skip
            if (this.mFeaturesMap.size() != 0 && this.uFeaturesMap.size() != 0 && !this.hasPredictionMatrixBeenComputed) {
                // check whether no new final feature vectors have been added in the mean time
                if (this.mFeaturesMap.size() == this.mostRecentMFeaturesMapSize
                    && this.uFeaturesMap.size() == this.mostRecentUFeaturesMapSize) {
                    System.out.println(String.format("Start Prediction Matrix Computation at %s", new Timestamp(System.currentTimeMillis())));
                    this.constructFeatureMatrices();
                    this.calculatePredictionMatrix();
                    this.hasPredictionMatrixBeenComputed = true;
                } else {
                    this.mostRecentMFeaturesMapSize = this.mFeaturesMap.size();
                    this.mostRecentUFeaturesMapSize = this.uFeaturesMap.size();
                }
            }
        });
    }

    @Override
    public void process(final Integer partition, final FeatureMessage msg) {
        if (this.context.topic().equals("movie-features-" + ALSApp.NUM_ALS_ITERATIONS)) {
            this.mFeaturesMap.put(msg.id, msg.features);
        } else if (this.context.topic().equals("user-features-" + ALSApp.NUM_ALS_ITERATIONS)) {
            this.uFeaturesMap.put(msg.id, msg.features);
        }
    }

    private void constructFeatureMatrices() {
        float[][] mFeaturesMatrixArray = new float[this.mFeaturesMap.size()][ALSApp.NUM_FEATURES];
        int i = 0;
        for (ArrayList<Float> mFeatures : this.mFeaturesMap.values()) {
            for (int j = 0; j < ALSApp.NUM_FEATURES; j++) {
                mFeaturesMatrixArray[i][j] = mFeatures.get(j);
            }
            i++;
        }
        this.mFeaturesMatrix = new FMatrixRMaj(mFeaturesMatrixArray);

        float[][] uFeaturesMatrixArray = new float[this.uFeaturesMap.size()][ALSApp.NUM_FEATURES];
        i = 0;
        for (ArrayList<Float> uFeatures : this.uFeaturesMap.values()) {
            for (int j = 0; j < ALSApp.NUM_FEATURES; j++) {
                uFeaturesMatrixArray[i][j] = uFeatures.get(j);
            }
            i++;
        }
        this.uFeaturesMatrix = new FMatrixRMaj(uFeaturesMatrixArray);
    }

    private void calculatePredictionMatrix() {
        FMatrixRMaj predictionMatrix = new FMatrixRMaj(this.uFeaturesMap.size(), this.mFeaturesMap.size());
        CommonOps_FDRM.multTransB(this.uFeaturesMatrix, this.mFeaturesMatrix, predictionMatrix);

        System.out.println(String.format("Done at %s", new Timestamp(System.currentTimeMillis())));
//        System.out.println("result");
//        System.out.println(predictionMatrix);

        // save as CSV

        return;
    }

    @Override
    public void close() {}
}