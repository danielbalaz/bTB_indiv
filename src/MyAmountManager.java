package btbcluster;

import broadwick.BroadwickException;
import broadwick.graph.Edge;
import broadwick.stochastic.AmountManager;
import broadwick.stochastic.SimulationEvent;
import broadwick.stochastic.SimulationState;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Create the amount manager that stochastically creates initial infection
 * states.
 */
@Slf4j
public class MyAmountManager implements AmountManager {

    public MyAmountManager(final MyMonteCarloScenario scenario) {
        this.scenario = scenario;
    }

    @Override
    public void performEvent(SimulationEvent event, int times) {
        log.trace("Performing event {} {} times", event, times);
        final int date = ((int) scenario.getSimulator().getCurrentTime());

        // DB: [Rates]
        // Need to do this 'times', if we have an E->T or T->I event, it will only be done once.
        for (int i = 0; i < times; i++) {

            if (event.getInitialState() instanceof InfectedCow) {
                // **** source: COW ****

                InfectedCow source = (InfectedCow) event.getInitialState();

                // DB: [DoNotMoveDeadCows]
                // This event may not be possible as the infected animal may have been removed via a RWHT.
                // ** COW exists?
                if (scenario.getInfectedCows().containsKey(source.getId())) {

                    if (event.getFinalState() instanceof InfectedCow) {
                        // **** COW -> COW transmission ****
                    
                        InfectedCow recipient = (InfectedCow) event.getFinalState();
                        
                        // if initial state is infectious (the id of the final state will be blank)
                        //           create an InfectedCow object on this farm and add it to the infectedcows collection.
                        // else 
                        //           update the infection status of the cow, the event has a link to the new object so we
                        //           can just copy the objects
                        
                        // ** new infection?
                        if (recipient.getId().isEmpty()) {

                            final int numInfectionsOnFarm = scenario.getFarmInfections().get(source.getFarmId()).size();
                            final int herdSize = scenario.getFarmData().get(source.getFarmId()).getHerdSize();

                            // ** any susceptibles?
                            if (numInfectionsOnFarm < herdSize) {
                                // **** COW -> COW transmission ****
                                // * update the SNPs in the COW.
                                source = updateSnps(source, date);
                                
                                log.trace("{}", String.format("Adding new infection onto farm %s (current size=%d) with %d infected animals",
                                        source.getFarmId(), herdSize, numInfectionsOnFarm));

                                // * create a COW object and add it to the FARM COLLECTION.
                                final InfectedCow cow = new InfectedCow(
                                        String.format("Cow_%05d", scenario.getNextCowId()),
                                        source.getFarmId(),
                                        source.getSnps(),
                                        source.getLastSnpGeneration(),
                                        recipient.getInfectionStatus());
                                scenario.getFarmInfections().get(cow.getFarmId()).add(cow);
                                scenario.getInfectedCows().put(cow.getId(), cow);

                                // * add COW into the Transmission TREE
                                // add source -> cow to the scenario.getResults().getTransmissionTree()
                                InfectionNode sourceNode = scenario.getResults().getTransmissionTree().getVertex(source.getId());

                                InfectionNode node = new InfectionNode(
                                        cow.getId(), cow.getFarmId(),
                                        cow.getSnps(), date, null, true);
                                scenario.getResults().getTransmissionTree().addVertex(node);
                                scenario.getResults().getTransmissionTree().addEdge(new Edge<>(sourceNode, node), sourceNode, node);
                                // record a cow -> cow transmission
                                // NOTE: Moved here. Let AOH know.
                                scenario.getResults().getCowCowTransmissions().add(1);
                            }
                        } else {
                            // Disease progress in Cow (next compartment)
                            source.setInfectionStatus(recipient.getInfectionStatus());
                        }
                    } else if (event.getFinalState() instanceof InfectedBadger) {
                        // **** COW -> BADGER transmission ****
                        
                        // find the farm the cow is on and select a connected reservoir.
                        Collection<Reservoir> connectedReservoirs = scenario.getSettings().getFarmReservoirs().get(source.getFarmId());
//                        Reservoir reservoir = (Reservoir) scenario.getGenerator().selectOneOf(connectedReservoirs);
                        Reservoir reservoir;
                        
                        // ** find a RESERVOIR that has at least one SUSCEPTIBLE BADGER
                        // NOTE: can be done better, look at comments for BADGER -> COW transmission
                        // for now ....
                        int j = 0;
                        do {
                            reservoir = (Reservoir) scenario.getGenerator().selectOneOf(connectedReservoirs);
    //                        connectedReservoirs.remove(reservoir);
                            j++;
                            if (j == connectedReservoirs.size()) break;
                        } while (scenario.getReservoirInfections().get(reservoir.getId()).size() >= scenario.getReservoirData().get(reservoir.getId()).getReservoirSize());
                        if (j == connectedReservoirs.size()) continue;
                        
                        // * update the SNPs in the COW.
                        source = updateSnps(source, date);
                        
                        // * create a BADGER object and add it to the RESERVOIR COLLECTION.
                        final InfectedBadger badger = new InfectedBadger(
                                String.format("Badger_%05d", scenario.getNextBadgerId()),
                                reservoir.getId(),
                                source.getSnps(),
                                source.getLastSnpGeneration());
                        scenario.getReservoirInfections().get(reservoir.getId()).add(badger);
                        scenario.getInfectedBadgers().put(badger.getId(), badger);
                        
                        // * add BADGER into the Transmission TREE
                        // add source -> badger to the scenario.getResults().getTransmissionTree()
                        InfectionNode sourceNode = scenario.getResults().getTransmissionTree().getVertex(source.getId());
                        
                        InfectionNode node = new InfectionNode(
                                badger.getId(), badger.getReservoirId(),
                                badger.getSnps(), date, null, false);
                        scenario.getResults().getTransmissionTree().addVertex(node);
                        scenario.getResults().getTransmissionTree().addEdge(new Edge<>(sourceNode, node), sourceNode, node);
                        // record a cow -> badger transmission
                        scenario.getResults().getCowBadgerTransmissions().add(1);
                    }
                }
            } else if (event.getInitialState() instanceof InfectedBadger) {
                // **** source: BADGER ****
                
                InfectedBadger source = (InfectedBadger) event.getInitialState();
                
                // ** BADGER exists?
                if (scenario.getInfectedBadgers().containsKey(source.getId())) {
                    
                    if (event.getFinalState() instanceof InfectedBadger) {
                        // **** BADGER -> BADGER transmission ****
                        
                        InfectedBadger recipient = (InfectedBadger) event.getFinalState();
                        
                        // ** new infection?
                        if (recipient.getId().isEmpty()) {
                            
                            final int numInfectionsOnReservoir = scenario.getReservoirInfections().get(source.getReservoirId()).size();
                            final int reservoirSize = scenario.getReservoirData().get(source.getReservoirId()).getReservoirSize();

                            // ** any susceptibles?
                            if (numInfectionsOnReservoir < reservoirSize) {
                                // **** BADGER -> BADGER transmission ****
                                // * update the SNPs in the BADGER.
                                source = updateSnps(source, date);
                                
                                log.trace("{}", String.format("Adding new infection onto reservoir %s (current size=%d) with %d infected animals",
                                        source.getReservoirId(), reservoirSize, numInfectionsOnReservoir));

                                // * create a BADGER object and add it to the RESERVOIR COLLECTION.
                                final InfectedBadger badger = new InfectedBadger(
                                        String.format("Badger_%05d", scenario.getNextBadgerId()),
                                        source.getReservoirId(),
                                        source.getSnps(),
                                        source.getLastSnpGeneration());
                                scenario.getReservoirInfections().get(badger.getReservoirId()).add(badger);
                                scenario.getInfectedBadgers().put(badger.getId(), badger);

                                // * add BADGER into the Transmission TREE
                                // add source -> badger to the scenario.getResults().getTransmissionTree()
                                InfectionNode sourceNode = scenario.getResults().getTransmissionTree().getVertex(source.getId());

                                InfectionNode node = new InfectionNode(
                                        badger.getId(), badger.getReservoirId(),
                                        badger.getSnps(), date, null, false);
                                scenario.getResults().getTransmissionTree().addVertex(node);
                                scenario.getResults().getTransmissionTree().addEdge(new Edge<>(sourceNode, node), sourceNode, node);
                                // record a badger -> badger transmission
                                scenario.getResults().getBadgerBadgerTransmissions().add(1);
                            }

                        } else {
                            // Disease progress in Badger (next compartment)
                            // at the moment this cannot happen as badgers only have 1 infectious compartment
//                            source.setInfectionStatus(recipient.getInfectionStatus());
                        }
                        
                    } else if (event.getFinalState() instanceof InfectedCow) {
                        // **** BADGER -> COW transmission ****

                        // ** find a FARM that has at least one SUSCEPTIBLE COW
                        // find the reservoir the badger is in and select a farm that is connected to this reservoir.
                        Reservoir reservoir = scenario.getReservoirData().get(source.getReservoirId());
                        String farmId;

                        // DB: [InfOnlySusc] Do not create more infected animals on a farm than the herd size on that farm
                        //     The condition in while() is there to select a destination farm with at least one susceptible cow
                        //     The if() break; statement is there to prevent from freezing in the loop,
                        //     if, for instance, the problem is not seelcting a farm with susceptibles, but,
                        //     that in the meantime, all susceptibles were moved away or new infecteds moved in or were newly infected! 
//                        Collection<String> connectedFarmIds = (Collection<String>) CloneUtils.deepClone(reservoir.getConnectedFarms());
                        Collection<String> connectedFarmIds = reservoir.getConnectedFarms();
                        int j = 0;
                        do {
                            farmId = (String) scenario.getGenerator().selectOneOf(connectedFarmIds);
//                            connectedFarmIds.remove(farmId);
                            j++;
                            if (j == connectedFarmIds.size()) break;
                        } while (scenario.getFarmInfections().get(farmId).size() >= scenario.getFarmData().get(farmId).getHerdSize());

                        if (j == connectedFarmIds.size()) continue;
                        Farm farm = scenario.getFarmData().get(farmId);
                        
                        // * update the SNPs in the BADGER.
                        source = updateSnps(source, date);
                        
                        // * create a COW object and add it to the FARM COLLECTION.
                        final InfectedCow cow = new InfectedCow(
                                String.format("Cow_%05d", scenario.getNextCowId()),
                                farmId,
                                source.getSnps(),
                                source.getLastSnpGeneration(),
                                ((InfectedCow) event.getFinalState()).getInfectionStatus());
                        scenario.getFarmInfections().get(cow.getFarmId()).add(cow);
                        scenario.getInfectedCows().put(cow.getId(), cow);
                        
                        log.trace("{}", String.format("Adding new infection onto farm %s (current size=%d) with %d infected animals from a badger",
                            farmId, farm.getHerdSize(),
                            scenario.getFarmInfections().get(farmId).size()));

                        // * add COW into the Transmission TREE
                        // add source -> cow to the scenario.getResults().getTransmissionTree()
                        InfectionNode sourceNode = scenario.getResults().getTransmissionTree().getVertex(source.getId());

                        InfectionNode node = new InfectionNode(
                                cow.getId(), farmId,
                                cow.getSnps(), date, null, true);
                        scenario.getResults().getTransmissionTree().addVertex(node);
                        scenario.getResults().getTransmissionTree().addEdge(new Edge<>(sourceNode, node), sourceNode, node);
                        // record a badger -> cow transmission
                        scenario.getResults().getBadgerCowTransmissions().add(1);
                    }
                }
                
            } else {
                throw new BroadwickException("Unknown event " + event);
            }
        }
    }

    private InfectedBadger updateSnps(InfectedBadger source, final int date) {
        source.getSnps().addAll(
                                ProjectSettings.generateSnp(
                                        scenario.getStep().getCoordinates().get("mutationRate"),
                                        date,
                                        scenario.getGenerator(),
                                        source.getLastSnpGeneration()));
        source.setLastSnpGeneration(date);
        
        return (source);
    }
    
    private InfectedCow updateSnps(InfectedCow source, final int date) {
        source.getSnps().addAll(
                                ProjectSettings.generateSnp(
                                        scenario.getStep().getCoordinates().get("mutationRate"),
                                        date,
                                        scenario.getGenerator(),
                                        source.getLastSnpGeneration()));
        source.setLastSnpGeneration(date);
        
        return (source);
    }
    
    @Override
    public String toVerboseString() {
        return "";
    }

    @Override
    public void resetAmount() {
        // not required
    }

    @Override
    public void save() {
        // not required
    }

    @Override
    public void rollback() {
        // not required
    }

    private final MyMonteCarloScenario scenario;
}
