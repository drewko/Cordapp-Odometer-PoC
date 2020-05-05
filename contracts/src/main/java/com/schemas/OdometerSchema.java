package com.schemas;

import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.UUID;

public class OdometerSchema extends MappedSchema {

    public OdometerSchema(){
        super(OdometerSchema.class, 1, Arrays.asList(PersistentOdometer.class));
    }

    @Entity
    @Table(name = "odometer_states")
    public static class PersistentOdometer extends PersistentState {
        @Column(name = "kilometers") private final int kilometers;
        @Column(name = "vin") private final String vin;
        @Column(name = "car") private final String car;
        @Column(name = "manufacturer") private final String manufacturer;
        @Column(name = "linear_id") private final UUID linearId;


        public PersistentOdometer(int kilometers, String vin, String car, String manufacturer, UUID linearId) {
            this.kilometers = kilometers;
            this.vin = vin;
            this.car = car;
            this.manufacturer = manufacturer;
            this.linearId = linearId;
        }

        public PersistentOdometer() {
            this.kilometers = 0;
            this.vin = null;
            this.car = null;
            this.manufacturer = null;
            this.linearId = null;
        }

        public int getKilometers() {
            return kilometers;
        }

        public String getVin() {
            return vin;
        }

        public String getCar() {
            return car;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public UUID getLinearId() {
            return linearId;
        }
    }
}
