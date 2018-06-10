package behaviour;

import agents.Taxi;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SimpleBehaviour;

public class CheckStateBehavior extends SimpleBehaviour {
    private final Taxi agent;
    public CheckStateBehavior(Taxi taxi){
        agent = taxi;
    }

    @Override
    public void action() {
        // Check call taxi self check
        agent.checkStatus();
    }

    @Override
    public boolean done() {
        return false;
    }
}
