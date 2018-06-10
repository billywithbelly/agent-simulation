package city;

import utils.io.In;
import utils.io.StdOut;
import utils.simulation.StdRandom;
import utils.shortestPath.DijkstraUndirectedSP;
import utils.shortestPath.Edge;
import utils.shortestPath.EdgeWeightedGraph;
import utils.shortestPath.Path;

import java.text.SimpleDateFormat;
import java.util.*;

public class City {
    /**
     * &k; is the parameter we use to decide by how much to split the edges of the graph
     * see {@link City}
     * <p>
     * &multiplier; is a bit of a hack, we needed to create intermediary edges in the graph
     * These would require more vertices and edges, so we just simply initialized the
     * values multiplied by 10 to accommodate the new nodes and edges
     * see {@link EdgeWeightedGraph}
     */
    public static final double k = 0.5;
    public static final int multiplier = 10;

    public EdgeWeightedGraph G;
    int totalCalls = 0;
    public final int taxiCenter = 27; //Taxi Center at vertex #25
    public int totalPassengers = 0;
    public static double last_req_distance;
    public ArrayList<Intersection> intersections;
    public ArrayList<Intersection> dropoffPoints;
    public ArrayList<Passenger> passengerArrayList;
    public static final HashMap<Integer, DijkstraUndirectedSP> pathLookup = new HashMap<>();

    public City() {
        In in = new In("src/main/resources/v_city.txt");
        generateCity(in);
    }

    public void generateCity(In in) {
        G = new EdgeWeightedGraph(in);
        this.intersections = extractIntersections(G);
        extendGraph(G);
        this.dropoffPoints = extractDropoffPoints(G);
        this.totalCalls = 0;
        this.totalPassengers = 0;
        this.passengerArrayList = new ArrayList<>();
    }

    public void generateCity(In in, int extend) {
        G = new EdgeWeightedGraph(in);
        this.intersections = extractIntersections(G);
        if (extend == 1) {
            extendGraph(G);
        }
        this.dropoffPoints = extractDropoffPoints(G);
        this.totalCalls = 0;
        this.totalPassengers = 0;
        this.passengerArrayList = new ArrayList<>();
    }

    public void clear() {
        this.G = null;
        pathLookup.clear();
        this.totalCalls = 0;
        this.totalPassengers = 0;
        this.intersections = null;
        this.dropoffPoints = null;
        this.passengerArrayList = null;
    }

    /**
     * Calculate shortest paths from source &w; to all other vertices &v_i;
     * if &pathLookup; contains the shortest path for source &w; return it,
     * otherwise update it
     *
     * @param G EdgeWeightedGraph.
     * @param w source node
     * @return an @ArrayList of @Intersections
     */
    public DijkstraUndirectedSP getShortestPaths(EdgeWeightedGraph G, int w) {
        if (pathLookup.get(w) == null) {
            DijkstraUndirectedSP sp = new DijkstraUndirectedSP(G, w);
            pathLookup.put(w, sp);
            return sp;
        }
        return pathLookup.get(w);
    }


    public void addPassenger(Intersection intersection, int id) {
        this.passengerArrayList.add(new Passenger(intersection, id));
    }

    public void setPassengerRoute(Passenger p) {
        ArrayList<Path> paths = getRoutes(G, p.origin.index, p.d);
        int rand = StdRandom.uniform(0, paths.size());
        Path destination = paths.get(rand);
        p.destinationNode = destination.v;
        p.route = destination;
    }

    /**
     * Extract a list of all intersection indices within the graph
     *
     * @param G EdgeWeightedGraph.
     * @return an @ArrayList of @Intersections
     */
    public ArrayList<Intersection> extractIntersections(EdgeWeightedGraph G) {
        Iterable<Edge> edges = G.edges();
        ArrayList<Intersection> list = new ArrayList<>(Collections.nCopies(G.V(), new Intersection()));
        HashMap seen = new HashMap();
        Intersection i = new Intersection();
        for (Edge e : edges) {
            i.index = e.either();
            if (seen.get(i.index) == null) {
                Iterable<Edge> adj = G.adj(i.index);
                for (Edge a : adj) {
                    if (i.index == a.other(a.either())) {
                        i.connections.add(a.either());
                    } else {
                        i.connections.add(a.other(a.either()));
                    }
                }
                list.set(i.index, i);
                seen.put(i.index, i.index);
                i = new Intersection();
            }
        }

        // Add the first index, this is a hack but I got tired of trying to
        // figure out why the above wasn't added element 0
        i.index = 0;
        Iterable<Edge> adj = G.adj(0);
        for (Edge a : adj) {
            if (i.index == a.other(a.either())) {
                i.connections.add(a.either());
            }
        }
        list.set(i.index, i);
        return list;
    }

    /**
     * Extract a list of all dropoff points, includes intersections as well
     *
     * @param G EdgeWeightedGraph.
     * @return an @ArrayList of @DropoffPoints
     */
    public ArrayList<Intersection> extractDropoffPoints(EdgeWeightedGraph G) {
        Iterable<Edge> adj = G.edges();
        ArrayList<Intersection> list = new ArrayList<>();
        HashMap seen = new HashMap();

        Intersection x = new Intersection();
        for (Edge e : adj) {
            x.index = e.other(e.either());
            if (seen.get(x.index) == null) {
                list.add(x);
                seen.put(x.index, x.index);
                x = new Intersection();
            }
        }
        Collections.reverse(list);
        return list;
    }

    /**
     * Split the weights of the edges into &k; sections. This is necessary
     * for allowing a taxi to stop at a point that is between the edges of
     * the original graph. Ex, split 1KM into 200m sections. 1/0.2
     *
     */
    private void extendGraph(EdgeWeightedGraph G) {
        Iterable<Edge> adj = G.edges();
        for (Edge e : adj) {

            double weight = e.weight();
            int initial_vertices = G.V();
            int intermediary_edges = (int) (weight / City.k);

            G.V(G.V() + (intermediary_edges - 1));
            G.E(G.E() + (intermediary_edges - 1));

            // Attach edge to first and last intermediary nodes
            G.addEdge(new Edge(e.other(e.either()), G.V() - (intermediary_edges - 1), City.k));
            G.addEdge(new Edge((G.V() - 1), e.either(), City.k));

            for (int i = 1; i < intermediary_edges - 1; i++) {
                G.addEdge(new Edge(initial_vertices, initial_vertices + 1, City.k));
                initial_vertices++;
            }
        }
    }

    /**
     * Returns a list of shortest paths from the source vertex &w; to all other vertices &v_i;
     * with a cost <= &d;.
     *
     * @param G the edge weighted graph
     * @param w the source vertex
     * @param d the distance to travel
     * @return a shortest path from the source vertex w to vertex v
     * as an iterable of Paths with distance <= d
     */
    public ArrayList<Path> getRoutes(EdgeWeightedGraph G, int w, double d) {
        DijkstraUndirectedSP sp = getShortestPaths(G, w);
        ArrayList<Path> list = new ArrayList<>();
        for (int v = 0; v < G.V(); v++) {
            if (sp.hasPathTo(v) && sp.distTo(v) == d) {
                Path p = new Path();

                p.w = w;
                p.v = v;
                p.weight = sp.distTo(v);
                for (Edge e : sp.pathTo(v)) {
                    p.list.add(e);
                }
                list.add(p);
            }
        }
        return list;
    }
}
