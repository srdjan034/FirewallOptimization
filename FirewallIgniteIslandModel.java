import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.lang3.SystemUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteRunnable;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.algorithm.PeriodicAction;
import org.moeaframework.algorithm.PeriodicAction.FrequencyType;
import org.moeaframework.analysis.plot.Plot;
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
import org.moeaframework.core.spi.OperatorFactory;
import org.moeaframework.core.variable.Permutation;
import org.moeaframework.core.variable.RealVariable;
import org.moeaframework.problem.ExternalProblem;

import parallel.SynchronizedMersenneTwister;
import parallel.SynchronizedNondominatedSortingPopulation;


public class FirewallIgniteIslandModel 
{
	
	public static int ISLANDS = 4;
	
	public static class FirewallIslandCallable implements IgniteCallable<NondominatedPopulation> 
	{
		
		private static final long serialVersionUID = -4006678228704675266L;

		private final int islandIndex;
		private String pathToExe = null;
		private Problem problem;
		private int migrationFrequency = 1000;
		private int numberOfEvaluations = 10000;
		
		public FirewallIslandCallable(int islandIndex, String pathToExe, 
				int numberOfEvaluations, int migrationFrequency) throws Exception 
		{
			super();
			this.islandIndex = islandIndex;
			this.pathToExe = pathToExe;
			this.numberOfEvaluations = numberOfEvaluations;
			this.migrationFrequency = migrationFrequency;
		}
		
		@Override
		public NondominatedPopulation call() throws Exception 
		{
			String[] commands = new String[1];
			commands[0] = pathToExe;
			Thread.sleep((islandIndex - 1) * 1000);
			this.problem = new FirewallProblem(commands);
			
			PRNG.setRandom(SynchronizedMersenneTwister.getInstance());
			
			final Ignite ignite = Ignition.allGrids().get(0);
			final SynchronizedNondominatedSortingPopulation population = new SynchronizedNondominatedSortingPopulation();
			final List<Solution> incomingSolutions = new ArrayList<Solution>();
			
			// listen for incoming migrants
			ignite.message().localListen("island" + islandIndex, new IgniteBiPredicate<UUID, Object>() 
			{
				private static final long serialVersionUID = -2051222258821382910L;

				@Override
				public boolean apply(UUID id, Object solution) 
				{
					synchronized (incomingSolutions) 
					{
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
					OperatorFactory.getInstance().getVariation(
							null,
							new Properties(),
							problem),
					new RandomInitialization(problem, 100));

			// define the periodic action for performing migrations
			PeriodicAction migrationAction = new PeriodicAction(algorithm, migrationFrequency, FrequencyType.EVALUATIONS) 
			{

				@Override
				public void doAction() 
				{
					int targetIndex = PRNG.nextInt(ISLANDS-1);
					
					if (targetIndex >= islandIndex) 
					{
						targetIndex += 1;
					}

					synchronized (population) 
					{
						Solution emigrant = population.get(PRNG.nextInt(population.size()));
						
						ignite.message().send("island" + targetIndex,
								emigrant.copy());
						
						synchronized (incomingSolutions) 
						{
							if (!incomingSolutions.isEmpty()) 
							{
								int originalSize = population.size();
								population.addAll(incomingSolutions);
								population.truncate(originalSize);
								incomingSolutions.clear();
							}
						}
					}
				}

			};

			while (migrationAction.getNumberOfEvaluations() < numberOfEvaluations) 
			{
				migrationAction.step();
			}
			
			problem.close();

			return migrationAction.getResult();
		}
		
	}
	
	public static void main(String[] args) throws Exception 
	{
		String pathToExe  = args[0];
		String pathToIgniteConfFile  = args[1];
		int numberOfEvaluations = Integer.parseInt(args[2]);
		int migrationFrequency  = Integer.parseInt(args[3]);
		ISLANDS = Integer.parseInt(args[4]);
		
		//check if the executable exists
		File file = new File(pathToExe);
		
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
		
		//check if the conf file exists
		file = new File(pathToIgniteConfFile);
		
		if (!file.exists()) 
		{
			System.err.println("Ignite conf file does not exists.");
			return;
		}
				
		Ignition.setClientMode(true);
		
		try (Ignite ignite = Ignition.start(pathToIgniteConfFile))
		{
			
			Collection<IgniteCallable<NondominatedPopulation>> islands = new ArrayList<>();
			
			for (int i = 0; i < ISLANDS; i++) 
			{
				islands.add(new FirewallIslandCallable(i+1, pathToExe, numberOfEvaluations, migrationFrequency));
			}
		
			Collection<NondominatedPopulation> results = ignite.compute().call(islands);
			
			NondominatedPopulation combined = new NondominatedPopulation();
			
			for (NondominatedPopulation result : results) 
			{
				for (Solution solution : result)
				{
					Permutation p = (Permutation)solution.getVariable(0);
									
					System.out.print("\n\nFitness = "  + (-solution.getObjectives()[0]) + " - ");
									
					int[] solutionArray = p.toArray();
					
					for (int i = 0; i < solutionArray.length; i++) 
					{
						System.out.print(solutionArray[i] + ", ");
					}
				}
				
				combined.addAll(result);
			}
			
			ignite.cluster().stopNodes();
		}
	}

}
