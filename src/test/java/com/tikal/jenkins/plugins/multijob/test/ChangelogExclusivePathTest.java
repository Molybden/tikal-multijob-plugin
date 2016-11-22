package com.tikal.jenkins.plugins.multijob.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xml.sax.SAXException;

import com.tikal.jenkins.plugins.multijob.MultiJobBuilder;
import com.tikal.jenkins.plugins.multijob.MultiJobBuilder.ContinuationCondition;
import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import com.tikal.jenkins.plugins.multijob.PhaseJobsConfig;
import com.tikal.jenkins.plugins.multijob.PhaseJobsConfig.KillPhaseOnJobResultCondition;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.tasks.Shell;

/**
 * @author Joachim Nilsson (JocceNilsson)
 */
public class ChangelogExclusivePathTest {

    /*
	 *

    public static class MyScm extends SCM implements Serializable {

        @Override
        public SCMDescriptor<MyScm> getDescriptor() {
            return new SCMDescriptor<MyScm>(null) {
                @Override
                public String getDisplayName() {
                    return null;
                }
            };
        }

        @Override
        public ChangeLogParser createChangeLogParser() {
            return new ChangeLogParser() {
                @Override
                public ChangeLogSet<? extends ChangeLogSet.Entry> parse(Run build, RepositoryBrowser<?> browser, File changelogFile)
                        throws IOException, SAXException {
                    final List<ChangeLogSet.Entry> list = new ArrayList<>();
                    list.add(new ChangeLogSet.Entry() {
                        @Override
                        public String getMsg() {
                            return "A commit";
                        }

                        @Override
                        public User getAuthor() {
                            return User.getUnknown();
                        }

                        @Override
                        public Collection<String> getAffectedPaths() {
                            return Arrays.asList("/a/b/c");
                        }
                    });
                    return new ChangeLogSet<ChangeLogSet.Entry>(build, browser) {
                        @Override
                        public boolean isEmptySet() {
                            return false;
                        }

                        @Override
                        public Iterator<Entry> iterator() {
                            return list.iterator();
                        }
                    };
                }
            };
        }

        @Override
        public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace, @Nonnull TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState baseline)
                throws IOException, InterruptedException {
            if (changelogFile == null) {
                changelogFile = File.createTempFile("changelog", ".xml");
            }
            if (!changelogFile.exists()) {
                changelogFile.createNewFile();
            }
        }

        @Override
        public SCMRevisionState calcRevisionsFromBuild(@Nonnull Run<?, ?> build, @Nullable FilePath workspace, @Nullable Launcher launcher, @Nonnull TaskListener listener)
                throws IOException, InterruptedException {
            return new SCMRevisionState() {
                @Override
                public String getUrlName() {
                    return "/a/b/c";
                }
            };
        }

    }
    */

	public interface SerializeableAnswer<T> extends Answer<T>, Serializable {
	}

	@Mock
	transient SCMDescriptor scmDescriptor;
	@Mock
	transient SCM scm;
	@Mock
	transient ChangeLogParser parser;
	@Mock
	transient ChangeLogSet set;
	@Mock
	transient ChangeLogSet.Entry entry;
	@Rule
	public transient JenkinsRule j = new JenkinsRule();

	private boolean changeSetEmpty = true;
	private List<String> affectedPaths = Collections.emptyList();

	@Before
	public void setup()
			throws IOException, InterruptedException, SAXException {
		initMocks(this);
		//scm = new MyScm();
		when(scm.getDescriptor()).thenReturn(scmDescriptor);
		when(scm.createChangeLogParser()).thenReturn(parser);
		doAnswer(createChangelog()).when(scm).checkout(any(Run.class), any(Launcher.class), any(FilePath.class), any(TaskListener.class), any(File.class), any(SCMRevisionState.class));
		when(scm.checkout(any(AbstractBuild.class), any(Launcher.class), any(FilePath.class), any(BuildListener.class), any(File.class))).thenCallRealMethod();
		when(parser.parse(any(Run.class), any(RepositoryBrowser.class), any(File.class))).thenReturn(set);
		when(parser.parse(any(AbstractBuild.class), any(File.class))).thenCallRealMethod();
		when(set.isEmptySet()).thenAnswer(getIsEmpty());
		when(set.iterator()).thenAnswer(getIterator());
		when(entry.getMsg()).thenReturn("A commit");
		when(entry.getAuthor()).thenReturn(User.getUnknown());
		when(entry.getAffectedPaths()).thenAnswer(getAffectedPaths());
	}

	private SerializeableAnswer<Iterator<ChangeLogSet.Entry>> getIterator() {
		return new SerializeableAnswer<Iterator<ChangeLogSet.Entry>>() {
			@Override
			public Iterator<ChangeLogSet.Entry> answer(InvocationOnMock invocationOnMock) throws Throwable {
				return Arrays.asList(entry).iterator();
			}
		};
	}

	private SerializeableAnswer<Boolean> getIsEmpty() {
		return new SerializeableAnswer<Boolean>() {
			@Override
			public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
				return changeSetEmpty;
			}
		};
	}

	private SerializeableAnswer<List<String>> getAffectedPaths() {
		return new SerializeableAnswer<List<String>>() {
			@Override
			public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
				return affectedPaths;
			}
		};
	}

	private SerializeableAnswer createChangelog() {
		return new SerializeableAnswer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				File changelogFile = invocation.getArgumentAt(4, File.class);
				if (changelogFile == null) {
					changelogFile = File.createTempFile("changelog", ".xml");
				}
				if (!changelogFile.exists()) {
					changelogFile.createNewFile();
				}
				return null;
			}
		};
	}

	private void mockEmptyChangeSet() {
		changeSetEmpty = true;
		affectedPaths = Collections.emptyList();
	}

	private void mockChangeSet(String... pathnameList)
			throws SAXException, IOException {
		changeSetEmpty = false;
		affectedPaths = Arrays.asList(pathnameList);
	}

	@Test
	public void testSkippingJobBasedOnChangelog() throws Exception {
		j.jenkins.getInjector().injectMembers(this);
		MultiJobProject multi = j.jenkins.createProject(MultiJobProject.class, "MultiTop");
		multi.setUseExclusivePath(true);
		FreeStyleProject free1 = j.jenkins.createProject(FreeStyleProject.class, "job1");
		free1.getBuildersList().add(new Shell("echo hello from job-1"));
		FreeStyleProject free2 = j.jenkins.createProject(FreeStyleProject.class, "job2");
		free2.getBuildersList().add(new Shell("echo hello from job-2"));
		List<PhaseJobsConfig> configList = new ArrayList<>();
		configList.add(new PhaseJobsConfig("job1", null, true, null, KillPhaseOnJobResultCondition.NEVER, false, false, "", 0, false, false, "", false, false, true, "a/b/.+"));
		configList.add(new PhaseJobsConfig("job2", null, true, null, KillPhaseOnJobResultCondition.NEVER, false, false, "", 0, false, false, "", false, false, false, ""));
		multi.getBuildersList().add(new MultiJobBuilder("Phase", configList, ContinuationCondition.SUCCESSFUL, MultiJobBuilder.ExecutionType.PARALLEL));
		multi.setScm(scm);
		mockEmptyChangeSet();
		j.assertBuildStatus(Result.SUCCESS, multi.scheduleBuild2(0, new UserCause()).get());
		mockChangeSet("a/b/c");
		j.assertBuildStatus(Result.SUCCESS, multi.scheduleBuild2(0, new UserCause()).get());
		assertLogContains("MultiJob should skip job2", multi, "Exclusive path: Skipping job2. Project is not affected by the SCM changes in this build.");
		assertLogContains("MultiJob should run job1", multi, "Starting build job job1.");
		assertLogContains("shell task writes 'hello from job-1' to log", free1, "hello from job-1");
		assertEquals("there should be one phase and three projects", 4, multi.getView().getRootItem(multi).size());
	}

	private void assertLogContains(String message, Project project, String expected)
			throws IOException {
		List<String> multiLog = project.getLastBuild().getLog(30);
		if (!multiLog.contains(expected)) {
			for (String line : multiLog) {
				message += "\n" + line;
			}
			Assert.fail(message);
		}
	}
}
