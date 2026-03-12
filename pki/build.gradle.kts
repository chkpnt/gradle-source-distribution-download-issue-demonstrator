plugins {
    id("de.chkpnt.truststorebuilder") version "0.6.0"
}

trustStoreBuilder {
    trustStore {
        source(".")
        path("truststore.jks")
    }
}
