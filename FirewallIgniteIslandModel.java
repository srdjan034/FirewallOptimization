package parallel.examples;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.SystemUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.lang.IgniteCallable;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.algorithm.PeriodicAction;
import org.moeaframework.algorithm.PeriodicAction.FrequencyType;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Problem;
import org.moeaframework.core.Solution;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.CrowdingComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;
import org.moeaframework.core.operator.GAVariation;
import org.moeaframework.core.operator.RandomInitialization;
import org.moeaframework.core.operator.TournamentSelection;
import org.moeaframework.core.operator.real.PM;
import org.moeaframework.core.operator.real.SBX;
import org.moeaframework.core.variable.Permutation;
import org.moeaframework.problem.ExternalProblem;

import parallel.SynchronizedMersenneTwister;
import parallel.SynchronizedNondominatedSortingPopulation;


public class FirewallIgniteIslandModel 
{
	
	public static final int ISLANDS = 4;
	
	public static class IslandCallable implements IgniteCallable<NondominatedPopulation> {
		
		private static final long serialVersionUID = -4006678228704675266L;

		private final int islandIndex;
		
		private final Problem problem;
		
		public IslandCallable(int islandIndex, Problem problem) {
			super();
			this.islandIndex = islandIndex;
			this.problem = problem;
		}
		
		@Override
		public NondominatedPopulation call() throws Exception {
			PRNG.setRandom(SynchronizedMersenneTwister.getInstance());
			
			final Ignite ignite = Ignition.allGrids().get(0);
			final SynchronizedNondominatedSortingPopulation population = new SynchronizedNondominatedSortingPopulation();
			final List<Solution> incomingSolutions = new ArrayList<Solution>();
			
			// listen for incoming migrants
			ignite.message().localListen("island" + islandIndex, new IgniteBiPredicate<UUID, Object>() {

				private static final long serialVersionUID = -2051222258821382910L;

				@Override
				public boolean apply(UUID id, Object solution) {
					synchronized (incomingSolutions) {
						incomingSolutions.add(((Solution)solution).copy());
					}
					
					return true;
				}
				
			});
			
			// define the algorithm
			NSGAII algorithm = new NSGAII(
					problem,
					population,
					null,
					new TournamentSelection(2, 
							new ChainedComparator(
									new ParetoDominanceComparator(),
									new CrowdingComparator())),
					new GAVariation(new SBX(1.0, 25.0), new PM(0.1, 30.0)),
					new RandomInitialization(problem, 100));

			// define the periodic action for performing migrations
			PeriodicAction migrationAction = new PeriodicAction(algorithm, 1000, FrequencyType.STEPS) {

				@Override
				public void doAction() {
					int targetIndex = PRNG.nextInt(ISLANDS-1);
					
					if (targetIndex >= islandIndex) {
						targetIndex += 1;
					}

					synchronized (population) {
						Solution emigrant = population.get(PRNG.nextInt(population.size()));
						
						ignite.message().send("island" + targetIndex,
								emigrant.copy());
						
						synchronized (incomingSolutions) {
							if (!incomingSolutions.isEmpty()) {
								int originalSize = population.size();
								population.addAll(incomingSolutions);
								population.truncate(originalSize);
								incomingSolutions.clear();
							}
						}
					}
				}

			};

			while (migrationAction.getNumberOfEvaluations() < 40000) {
				migrationAction.step();
			}

			return migrationAction.getResult();
		}
		
	}
	
	public static void main(String[] args) throws IOException, InterruptedException 
	{
		try (Ignite ignite = Ignition.start("config/example-ignite.xml")) 
		{
			int port = 16800;
			for (int i = 0; i < ISLANDS; i++) 
			{
				
				//check if the executable exists
				File file = new File("./examples/dtlz2_socket.exe");
						
				if (!file.exists()) 
				{
					if (!SystemUtils.IS_OS_UNIX) 
					{
						System.err.println("This example only works on POSIX-compliant systems; see the Makefile for details");
						return;
					}
							
					System.err.println("Please compile the executable by running make in the ./examples/ folder");
					return;
				}
				
				//run the executable and wait one second for the process to startup
				Process pb = new ProcessBuilder(file.toString(), "" + (++port)).start();
				Thread.sleep(1000);
				
			}
			
			Collection<IgniteCallable<NondominatedPopulation>> islands = new ArrayList<>();
			
			for (int i = 0; i < ISLANDS; i++) 
			{
				Problem problem = new FirewallProblem("localhost", port--);
				islands.add(new IslandCallable(i+1, problem));

			}
		
			Collection<NondominatedPopulation> results = ignite.compute().call(islands);
			
			NondominatedPopulation combined = new NondominatedPopulation();
			
			for (NondominatedPopulation result : results) 
			{
				for (Solution solution : result)
				{
					Permutation p = (Permutation)solution.getVariable(0);
									
					System.out.print("Fitness = "  + (-solution.getObjectives()[0]) + " - ");
									
					int[] solutionArray = p.toArray();
					for (int i = 0; i < solutionArray.length; i++) 
					{
						System.out.print(solutionArray[i] + ", ");
					}
				}
				
				combined.addAll(result);
			}
		}
	}

}
