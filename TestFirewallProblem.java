package parallel.examples;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.apache.commons.lang3.SystemUtils;
import org.moeaframework.Executor;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.Permutation;



public class TestFirewallProblem {

	public static void main(String[] args) throws IOException, InterruptedException 
	{
		String port = "16801";
		
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
		Process pb = new ProcessBuilder(file.toString(), port).start();
		
		Thread.sleep(1000);
		
		try 
		{
			//configure and run the DTLZ2 function
			NondominatedPopulation result = new Executor()
							.withProblemClass(FirewallProblem.class)
							.withAlgorithm("NSGAII")
							.withMaxEvaluations(10000)
							.distributeOnAllCores()
							.run();
			
			//display the results
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
			
		} 
		catch (Exception e) 
		{
			System.out.println("Greska : " + e.getMessage());
			
			// U slucaju greske procitaj poruke iz konzole c++-a
			InputStream is = pb.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
									
			String line = null;
			while((line = br.readLine()) != null)
				System.out.println("\n" + line);
		}
		


	}

}
