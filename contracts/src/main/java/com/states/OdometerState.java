package com.states;

import com.contracts.OdometerContract;
import com.schemas.OdometerSchema;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.LinearState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

// *********
// * State *
// *********

@BelongsToContract(OdometerContract.class)
public class OdometerState implements LinearState, QueryableState {

    private final int kilometers;
    private final String vin;
    private final Party car;
    private final Party manufacturer;

    private final UniqueIdentifier linearId;


    public OdometerState(int kilometers, String vin, Party car, Party manufacturer, UniqueIdentifier linearId) {
        this.kilometers = kilometers;
        this.vin = vin;
        this.car = car;
        this.manufacturer = manufacturer;
        this.linearId = linearId;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(car, manufacturer);
    }

    @Override public UniqueIdentifier getLinearId() {
        return linearId;
    }

    public AbstractParty getCar(){
        return car;
    }

    public Party getManufacturer() {
        return manufacturer;
    }

    public int getKilometers() {
        return kilometers;
    }

    @NotNull
    @Override
    public PersistentState generateMappedObject(@NotNull MappedSchema schema) {
        if(schema instanceof OdometerSchema){
            return new OdometerSchema.PersistentOdometer(
                    this.kilometers,
                    this.vin,
                    this.car.getName().toString(),
                    this.manufacturer.getName().toString(),
                    this.linearId.getId());
        }
        else{
            throw new IllegalArgumentException("Unrecognised schema");
        }
    }

    @NotNull
    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return Arrays.asList(new OdometerSchema());
    }

    @Override
    public String toString() {
        return "OdometerState{" +
                "kilometers=" + kilometers +
                ", vin='" + vin + '\'' +
                ", car=" + car +
                ", manufacturer=" + manufacturer +
                ", linearId=" + linearId +
                '}';
    }
}