package com.bakdata.demo.apps;

import com.bakdata.demo.producers.NetflixDataFormatProducer;

import java.io.File;


class ALSAppRunner {
    public static void main(String[] args) {
        ALSApp alsApp = new ALSApp();

        String pathToTestDataFile = new File("./testdata/movie_ratings_very_small").getAbsolutePath();
        NetflixDataFormatProducer producer = new NetflixDataFormatProducer(pathToTestDataFile);

        try {
            producer.runProducer();
            alsApp.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
