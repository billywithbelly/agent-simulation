package agents;

import behaviour.ManageCallBehaviour;
import city.City;
import city.Intersection;
import city.Request;
import city.Passenger;
import jade.core.AID;
import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import utils.misc.Shift;
import utils.simulation.CallGen;
import utils.simulation.Timer;
import utils.simulation.StdRandom;
import utils.io.In;
import utils.io.Out;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaxiCoordinator extends Agent {
    private static final Out out = new Out("src/main/resources/output.txt");
    public City vCity;
    public Date nextTime = null;
    public int calls = 0;
    private int numOffTaxi = 0;
    public final ArrayList<AID> lstTaxi = new ArrayList<>(0);
    public Request lastRequest;
    private ArrayList<Passenger> passengerArrayList;
    public Timer runtime;

    public void out(String newLine) {
        out.println(newLine);
    }

    protected void setup() {
        In in = new In("src/main/resources/v_city.txt");

        System.out.println("Setup agent");

        System.out.println("Generating City");
        vCity = new City();
        vCity.generateCity(in);
        passengerArrayList = new ArrayList<>();

        // Starting timer
        String s = "08:00:00";
        Date input = null;
        try {
            input = new SimpleDateFormat("HH:mm:ss").parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(input);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, cal1.get(Calendar.HOUR));
        cal.set(Calendar.MINUTE, cal1.get(Calendar.MINUTE));
        cal.set(Calendar.SECOND, cal1.get(Calendar.SECOND));

        runtime = new Timer(cal.getTime(), 1);

        // create taxis
        for (int i = 1; i <= 4; i++) {
            this.addTaxi(new Intersection(this.vCity.taxiCenter), Shift.TIME_3AM_TO_1PM);
        }
        for (int i = 1; i <= 4; i++) {
            this.addTaxi(new Intersection(this.vCity.taxiCenter), Shift.TIME_6PM_TO_4AM);
        }
        for (int i = 1; i <= 4; i++) {
            this.addTaxi(new Intersection(this.vCity.taxiCenter), Shift.TIME_9AM_TO_7PM);
        }

        nextTime = nextCall(runtime.getDate());

        addBehaviour(new ManageCallBehaviour(this));

    }

    protected void takeDown() {
        System.out.println("Taxi-agent " + getAID().getName() + "is offline");
        // Make this agent terminate
        doDelete();
    }


    public void receiveCall(Passenger passenger, Intersection intersection) {
        intersection.receiveCall(passenger);
        this.passengerArrayList.add(passenger);
        this.vCity.passengerArrayList.add(passenger);
        System.out.println("[" + runtime.toString() + "]" + "  Received a call from Passenger " + passenger.id + "...");
    }

    public Date nextCall(Date currentTime) {
        return CallGen.nextCall(currentTime);
    }

    /**
     * Choose a random intersection but not Taxi Center
     *
     * @param taxiCenter
     * @return
     */
    public int pickRandomIntersectionIndex(ArrayList<Intersection> intersections, int[] taxiCenter) {
        int index;
        do {
            index = StdRandom.uniform(0, intersections.size() - 1);
        } while (find(intersections.get(index).index, taxiCenter));

        return index;

    }

    /**
     * Choose a random intersection but not Taxi Center
     *
     * @param taxiCenter
     * @return
     */
    public int pickRandomDropoffIndex(ArrayList<Intersection> dropoffPoints, int[] taxiCenter) {
        int index;
        do {
            index = StdRandom.uniform(0, dropoffPoints.size() - 1);
        } while (find(dropoffPoints.get(index).index, taxiCenter));

        return index;

    }

    private boolean find(int index, int[] array) {
        for (int i : array) {
            if (i == index)
                return true;
        }
        return false;
    }

    // Check if this Intersection must be process a pending call
    public boolean isCallAvailable(Date nextCall, Date currentTime) {
        return nextCall != null && nextCall.before(currentTime);
    }

    private void addTaxi(Intersection point, Shift shift) {
        Object[] params = {this.vCity, point, shift, numOffTaxi, runtime};
        ContainerController cc = getContainerController();

        try {
            AgentController new_agent = cc.createNewAgent(String.valueOf(numOffTaxi), "agents.Taxi", params);
            new_agent.start();
            lstTaxi.add(new AID(String.valueOf(numOffTaxi++), AID.ISLOCALNAME));
        } catch (StaleProxyException ex) {
            Logger.getLogger(TaxiCoordinator.class.getName()).log(Level.WARNING, null, ex);
        }
    }

    public static void main(String[] args) {
        String[] arg = {"-gui", "-agents", "agents.TaxiCoordinator:agents.TaxiCoordinator"};
        jade.Boot.main(arg);
    }
}
