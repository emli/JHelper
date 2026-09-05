package name.admitriev.jhelper.configuration;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The profile handed to CLion's runner has to be a {@link RunConfiguration}, not merely a
 * {@link RunProfile}: {@code CLionDebugProfileRunner.doExecute} casts it and passes it to its vetoers.
 * An earlier version wrapped the configuration in a plain {@code RunProfile} and failed at runtime with
 * a ClassCastException, which these tests exist to catch.
 */
public class TaskRunnerTest {

	/**
	 * Stands in for a CMake run configuration. Never instantiated, so it can stay abstract.
	 *
	 * {@code clone} has to be redeclared: {@link RunConfiguration} narrows its return type, which
	 * conflicts with {@code Object.clone} being protected.
	 */
	private abstract static class FakeConfiguration implements RunConfiguration {
		@Override
		public abstract RunConfiguration clone();
	}

	/** A configuration reached through a superclass, as the real ones are. */
	private abstract static class InheritedConfiguration extends FakeConfiguration {
	}

	@Test
	public void collectsRunConfigurationFromDirectlyImplementedInterfaces() {
		assertTrue(TaskRunner.allInterfacesOf(FakeConfiguration.class).contains(RunConfiguration.class));
	}

	@Test
	public void collectsInterfacesInheritedFromSuperclasses() {
		assertTrue(TaskRunner.allInterfacesOf(InheritedConfiguration.class).contains(RunConfiguration.class));
	}

	@Test
	public void collectsInterfacesExtendedByOtherInterfaces() {
		// RunConfiguration extends RunProfile, so a proxy over the collected set can also stand in
		// wherever a RunProfile is expected
		assertTrue(TaskRunner.allInterfacesOf(FakeConfiguration.class).contains(RunProfile.class));
	}

	@Test
	public void findsNoInterfacesForAPlainClass() {
		assertFalse(TaskRunner.allInterfacesOf(Object.class).contains(RunConfiguration.class));
	}

	@Test
	public void proxyOverCollectedInterfacesIsARunConfiguration() {
		Set<Class<?>> interfaces = TaskRunner.allInterfacesOf(FakeConfiguration.class);
		Object proxy = Proxy.newProxyInstance(
				TaskRunnerTest.class.getClassLoader(),
				interfaces.toArray(new Class<?>[0]),
				(instance, method, args) -> null
		);

		// the assertion that the ClassCastException came down to
		assertTrue("proxy must satisfy the cast CLion's runner performs", proxy instanceof RunConfiguration);
		assertTrue(proxy instanceof RunProfile);
	}
}
