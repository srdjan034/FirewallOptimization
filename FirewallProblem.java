import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.EncodingUtils;
import org.moeaframework.core.variable.Permutation;
import org.moeaframework.problem.ExternalProblem;

public class FirewallProblem extends ExternalProblem 
{

	public FirewallProblem(String[] command) throws IOException {
		super(command);
	}

	private int n = 1000;
	
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
		solution.setVariable(0, EncodingUtils.newPermutation(n));

		return solution;
	}

}
