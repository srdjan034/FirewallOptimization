import java.io.IOException;
import java.net.InetAddress;

import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.Permutation;
import org.moeaframework.problem.ExternalProblem;

public class FirewallProblem extends ExternalProblem 
{
	private int n = 1000;

	public FirewallProblem() throws IOException 
	{
		super("localhost", ExternalProblem.DEFAULT_PORT);
	}
	
	public FirewallProblem(int n) throws IOException 
	{
		this.n = n;
	}

	@Override
	public String getName() 
	{
		return "FirewallProblem";
	}

	@Override
	public int getNumberOfVariables() 
	{
		return 1;
	}

	@Override
	public int getNumberOfObjectives() 
	{
		return 1;
	}

	@Override
	public int getNumberOfConstraints() 
	{
		return 0;
	}

	@Override
	public Solution newSolution() 
	{
		Solution solution = new Solution(1, 1);
		solution.setVariable(0, new Permutation(n));
		return solution;
	}

}
