/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.copyartifact;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class CopierTest {

    @Test
    public void test_init() throws IOException, InterruptedException {
        Run runSrc = Mockito.mock(Run.class);
        AbstractBuild abDst = Mockito.mock(AbstractBuild.class);

        // make sure legacy parameter types (AbstractBuild) work on all impl variants
        for (C c : new C[] {new C1(), new C2(), new C3()}) {
            c.init(runSrc, abDst, null, null);
            Assert.assertEquals(1, c.callCnt);
        }
        C4 c4 = new C4();
        c4.init(runSrc, abDst, null, null);
        Assert.assertEquals(2, c4.callCnt);

        // test with new param types (Run dst type)

        // C1 and C2 both impl the non-legacy init methods, so should work fine.
        testRunDst(new C1(), 1); // should work
        testRunDst(new C2(), 1); // should work

        testRunDst(new C4(), 1); // should work since it extends C2, which was updated with the new init method

        // C3 only implements the legacy AbstractBuild init method (and not the Run
        // version). Copier.init(Run, Run) will get called, but should throw an AbstractMethodError
        try {
            testRunDst(new C3(), 0);
            Assert.fail("expected AbstractMethodError");
        } catch (AbstractMethodError e) {
            Assert.assertEquals("Invalid call to Copier.init(Run src, Run dst, FilePath, FilePath), passing an AbstractBuild " +
                    "instance for the 'dst' arg when hudson.plugins.copyartifact.CopierTest$C3 does not implement the deprecated " +
                    "version of 'init' that takes an AbstractBuild. Please supply a Run instance for the 'dst' arg.", e.getMessage());
        }
    }

    public void testRunDst(C c, int expectedCnt) throws IOException, InterruptedException {
        Run runSrc = Mockito.mock(Run.class);
        Run runDst = Mockito.mock(Run.class);
        c.init(runSrc, runDst, null, null);
        Assert.assertEquals(expectedCnt, c.callCnt);
    }

    private class C extends Copier {
        protected int callCnt;
        @Override
        public void copyOne(FilePath source, FilePath target, boolean fingerprintArtifacts) throws IOException, InterruptedException {
        }
        @Override
        public Copier clone() {
            return null;
        }
    }

    private class C1 extends C {
        // Overrides both init methods (for whatever reason :) )
        @Override
        public void init(Run src, Run<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
            callCnt++;
        }

        @Override
        public void init(Run src, AbstractBuild<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
            callCnt++;
        }
    }

    private class C2 extends C {
        // Override init that uses the Run dst
        @Override
        public void init(Run src, Run<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
            callCnt++;
        }
    }

    private class C3 extends C {
        // Override init that uses the deprecated AbstractBuild dst
        @Override
        public void init(Run src, AbstractBuild<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
            callCnt++;
        }
    }

    // C4 extends C2, which is a Copier impl that's been updated to use the newer version of the init method.
    // C4 was overriding C2.init() but because of the C2 update, it is now overriding the Copier.init().
    // Call to super should be fine so long as dst is left as an AbstractBuild.
    private class C4 extends C2 {
        // Override init that uses the deprecated AbstractBuild dst + call super
        @Override
        public void init(Run src, AbstractBuild<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
            callCnt++;
            super.init(src, dst, srcDir, baseTargetDir);
        }
    }
}
