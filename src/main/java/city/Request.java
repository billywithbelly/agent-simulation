package city;

import jade.core.AID;

import java.io.Serializable;

public class Request implements Serializable {
    public final Intersection origin;
    public final Intersection destination;
    public Bid bid;
    public final int passengerID;
    public AID bidder;

    public Request(Intersection origin, Intersection destination, int passengerID) {
        this.origin = origin;
        this.destination = destination;
        this.bidder = new AID();
        this.passengerID = passengerID;
        this.bid = new Bid();
    }

}
