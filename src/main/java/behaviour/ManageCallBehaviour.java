package behaviour;

import agents.Taxi;
import agents.TaxiCoordinator;
import city.*;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import utils.misc.Activity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class Handle the Call Generation and send the request to taxis for auction
 */
public class ManageCallBehaviour extends Behaviour {
    private AID bestTaxi = new AID(); // The agent who provides the best offer
    private double bestPrice; // The best offered price
    private double companyPayoff;
    private double taxiPayoff;
    private double bestTaxiBid;
    private int[] taxiPayoffList = new int[13];
    private int repliesCnt = 0; // The counter of replies from seller agents
    private MessageTemplate mt; // The template to receive replies
    private Activity activity = Activity.WAITING_FOR_CALLS;
    private final TaxiCoordinator agent;
    private Request lastBestRequest;
    private final ArrayList<Request> biddingList = new ArrayList<>();
    private ArrayList<AID> taxiInThisRound = new ArrayList<>();
    private boolean waiting_for_response = false;
    private int totalCompanyPayoff = 0;

    public ManageCallBehaviour(TaxiCoordinator coordinator) {
        agent = coordinator;
    }

    private void nextCall() {
        agent.nextTime = agent.nextCall(agent.runtime.getDate());
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public void action() {
        //noinspection InfiniteLoopStatement,InfiniteLoopStatement,InfiniteLoopStatement,InfiniteLoopStatement,InfiniteLoopStatement,InfiniteLoopStatement,InfiniteLoopStatement
        for (int t = 0; true; t++) {
            agent.runtime.tick();
            try {
                Thread.sleep(1);
            } catch (Exception ignored) {
            }

            // 2 . Waiting for next call
            if (activity == Activity.WAITING_FOR_CALLS) {
                if (agent.isCallAvailable(agent.nextTime, agent.runtime.getDate())) {
                    // 3. Pick Random Node but not taxi center
                    int[] exclude = {agent.vCity.taxiCenter};
                    int nextIndex = agent.pickRandomIntersectionIndex(agent.vCity.intersections, exclude);
                    Intersection intersection = agent.vCity.intersections.get(nextIndex);

                    // 4. Receive call
                    System.out.println("---------------------------------------------------------------------------------------");
                    Passenger p = new Passenger(intersection, agent.calls++);
                    agent.vCity.totalPassengers++;
                    City.last_req_distance = p.d;
                    agent.receiveCall(p, intersection);
                    // 5. DO ACTION PROCESS HERE

                    // Pick random destination
                    int[] exclude2 = {agent.vCity.taxiCenter, nextIndex};
                    int destination = agent.pickRandomDropoffIndex(agent.vCity.dropoffPoints, exclude2);

                    //System.out.println("("+agent.runtime.toString()+")(Call " + agent.calls + ")");
                    System.out.println("(" + agent.runtime.toString() + ")  Calling from Node " + intersection.index + " to " + destination);
                    agent.out("Call " + intersection.index);

                    // Send Request to available taxi
                    agent.lastRequest = new Request(agent.vCity.intersections.get(nextIndex), new Intersection(agent.vCity.dropoffPoints.get(destination).index), agent.calls);
                    sentRequest();

                    // 6. Set next Time to call. ONly if step is 0 that means that is waiting for call
                    if (activity == Activity.WAITING_FOR_CALLS) {
                        nextCall();
                    }

                }
            } else {
                sentRequest();
            }

        }
    }

    private void sentRequest() {
        //System.out.println(activity.toString());
        switch (activity) {
            case WAITING_FOR_CALLS:
                //bestTaxi = new AID();
                // Send the cfp to all sellers
                System.out.println("(" + agent.runtime.toString() + ")  Sending request to all agents");
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                for (int i = 0; i < agent.lstTaxi.size(); ++i) {
                    cfp.addReceiver(agent.lstTaxi.get(i));

                }
                try {
                    cfp.setContentObject(agent.lastRequest);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                cfp.setConversationId("auction");
                cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
                agent.send(cfp);
                // Prepare the template to get proposals
                mt = MessageTemplate.and(MessageTemplate.MatchConversationId("auction"),
                        MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                activity = Activity.WAITING_FOR_BIDS;
                break;
            case WAITING_FOR_BIDS:
                // Receive all proposals/refusals from seller agents
                ACLMessage reply = agent.receive(mt);
                Request response = null;
                if (reply != null) {

                    // Reply received
                    if (reply.getPerformative() == ACLMessage.PROPOSE) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(reply.getByteSequenceContent());
                        ObjectInput in;
                        try {
                            in = new ObjectInputStream(bis);
                            response = ((Request) in.readObject());
                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        if (reply.getSender() == null) {
                            System.out.println("Cannont find sender");
                            System.exit(1);
                        }
                        // This is an offer
                        System.out.println("(" + agent.runtime.toString() + ")  Offer: Reply from " + reply.getSender().getLocalName() + " : " + (response != null ? (int)response.bid.taxiBid : 0) + " NT");

                        assert response != null;
                        response.bidder = reply.getSender();
                        biddingList.add(response);
                    } else {
                        if (reply.getSender() == null) {
                            System.out.println("Cannont find sender");
                            System.exit(1);
                        }
                        System.out.println("(" + agent.runtime.toString() + ")  Reply from " + reply.getSender().getLocalName() + " : " + reply.getContent());
                        //System.out.println("this is the raw content="+reply.getContent());
                    }
                    repliesCnt++;
                    if (repliesCnt >= agent.lstTaxi.size()) {
                        boolean bargainResult = processBids();
                        if (bargainResult) // We received all replies
                            activity = Activity.PROCESSING_BIDS;
                        else
                            activity = Activity.WAITING_FOR_CALLS;
                    }
                } else {
                    block();
                }
                break;
            case PROCESSING_BIDS:
                //
                try {
                    Thread.sleep(5);
                } catch (Exception ignored) {
                }

                if (bestTaxi == null) {
                    /** TODO fix this...
                     * this often starts a non-stop loop do to the state of taxi...
                     * @Link Taxi.checkStatus
                     * I would suggest that we add a state called Activity.NOT_ON_DUTY to show
                     * that the taxi is simply not on duty instead of some other reasons,
                     * and force wake up a taxi under some method
                    */
                    if (!waiting_for_response) {
                        System.out.println("Cannot find best taxi, all taxis are busy...\nLet's send the request" +
                                " to all the taxis again...");
                        System.out.println("Unable to get bid...\nLet's find the next passenger...");
                    }
                    activity = Activity.WAITING_FOR_CALLS;

                    //
                    nextCall();
                    repliesCnt = 0;
                    bestPrice = 0;
                    bestTaxi = null;
                    activity = Activity.WAITING_FOR_CALLS;
                    System.out.println("(" + agent.runtime.toString() + ")  ");
                    //
                    //waiting_for_response = true;
                    break;
                } else {
                    waiting_for_response = false;
                    System.out.println("(" + agent.runtime.toString() + ")  Bid won by " + bestTaxi.getLocalName() + " : " + String.format( "%.2f", bestTaxiBid )+ " | CompanyPayoff : " + String.format( "%.2f", companyPayoff ) + " | TaxiPayoff : " + String.format( "%.2f", taxiPayoff ));
                    taxiPayoffList[Integer.parseInt(bestTaxi.getLocalName())] += (int)taxiPayoff;

                    System.out.println("            TaxiPayoffList Array: " + Arrays.toString(taxiPayoffList));
                    System.out.println("            Total company payoff: " + totalCompanyPayoff);
                }
                // Sending confirmation to taxi for best offer
                ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                order.addReceiver(bestTaxi);
                try {
                    order.setContentObject(lastBestRequest);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                order.setConversationId("auction");
                order.setReplyWith("call" + System.currentTimeMillis());
                agent.send(order);
                // Prepare the template to get the purchase order reply
                mt = MessageTemplate.and(MessageTemplate.MatchConversationId("auction"),
                        MessageTemplate.MatchInReplyTo(order.getReplyWith()));

                activity = Activity.WAITING_TAXI_CONFIRMATION;
                //SEND MESSAGE TO CONFIRM BID ACCEPTED
                break;
            case WAITING_TAXI_CONFIRMATION:
                // RESPONSE OF TAXI WITH JOB ALLOCATED
                ACLMessage confirmation = agent.receive(mt);
                if (confirmation != null) {
                    switch (confirmation.getPerformative()) {
                        case ACLMessage.CONFIRM:
                            nextCall();
                            repliesCnt = 0;
                            bestPrice = 0;
                            bestTaxi = null;
                            activity = Activity.WAITING_FOR_CALLS;
                            System.out.println("(" + agent.runtime.toString() + ")  ");
                            break;
                        case ACLMessage.DISCONFIRM:
                            System.out.println("Error allocation job");

                    }
                } else {
                    block();
                }
                break;
        }
    }

    private Boolean processBids() {
        double lowestBid, secondLowestBid, lowestCo, secondLowestCo;
        lowestBid = secondLowestBid = Integer.MAX_VALUE;
        lowestCo = secondLowestCo = Integer.MAX_VALUE;

        for (Request r : biddingList) {
            if (r.bid.taxiBid < lowestBid && r.bid.taxiBid >= 0) {
                secondLowestBid = lowestBid;
                lowestBid = r.bid.taxiBid;
                lowestCo = r.bid.company;
                bestTaxi = r.bidder;
                lastBestRequest = r;
            } else if (r.bid.taxiBid < secondLowestBid && r.bid.taxiBid != lowestBid && r.bid.taxiBid >= 0) {
                secondLowestBid = r.bid.taxiBid;
                secondLowestCo = r.bid.company;
            }
        }

        // if bidding list is empty ---
        if (secondLowestCo == Integer.MAX_VALUE && lowestCo != Integer.MAX_VALUE)
            secondLowestCo = lowestCo;
        else
            secondLowestCo = 0;

        if (secondLowestBid == Integer.MAX_VALUE && lowestBid != Integer.MAX_VALUE)
            secondLowestBid = lowestBid;
        else
            secondLowestBid = 0;
        // if bidding list is empty ---


        if ((secondLowestCo - secondLowestBid) <= 0) {
            secondLowestCo = lowestCo;
            secondLowestBid = lowestBid;
        }

        if (lastBestRequest == null) {
            // TODO fix this...
            System.out.println("Cannot find best response, let's go back and ask all the taxis again...");
            activity = Activity.WAITING_FOR_CALLS;
            System.exit(1);
            return false;
        }
        /*
        lastBestRequest.bid.company = 0.3 * (secondLowestCo - secondLowestPayoff);
        System.out.println("SecondLowestCo" + secondLowestCo);
        System.out.println("SecondLowestPayoff" + secondLowestPayoff);
        System.out.println("lastBestRequest.bid.company" + lastBestRequest.bid.company);
        // TODO figure out why payoff calculated like this
        //lastBestRequest.bid.payOff = secondLowestPayoff - lastBestRequest.bid.company;
        */
        //charge_rate_per_kilometer - gas_cost_per_kilometer
        companyPayoff = 0.3*(60-4)*lastBestRequest.bid.chargeable_dist - secondLowestBid;
        totalCompanyPayoff += companyPayoff;
        taxiPayoff = (60*lastBestRequest.bid.chargeable_dist) - companyPayoff - (4*lastBestRequest.bid.total_dist);
        lastBestRequest.bidder = bestTaxi;
        bestTaxiBid = lowestBid;

        //bestPrice = lastBestRequest.bid.taxiBid;

        biddingList.clear();
        //System.out.println("I Have Arrived");


        // if the process is successful, return true
        return true;
    }

    public boolean done() {
        return ((activity == Activity.PROCESSING_BIDS && bestTaxi == null) || activity == Activity.JOB_ALLOCATED);
    }
}
