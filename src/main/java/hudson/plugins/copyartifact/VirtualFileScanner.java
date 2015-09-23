package hudson.plugins.copyartifact;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.StringTokenizer;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.util.VirtualFile;

import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.apache.tools.ant.types.selectors.TokenizedPath;
import org.apache.tools.ant.types.selectors.TokenizedPattern;
import org.codehaus.plexus.util.StringUtils;

/**
 * lists up files in {@link VirtualFile}
 * with ant file patterns.
 * 
 * @since 2.0
 */
public class VirtualFileScanner {
    private final List<TokenizedPath> includeNonPatternList;
    private final List<TokenizedPattern> includePatternList;
    
    private final List<TokenizedPath> excludeNonPatternList;
    private final List<TokenizedPattern> excludePatternList;
    
    /**
     * @param includes
     * @param excludes
     * @param useDefaultExcludes
     */
    public VirtualFileScanner(@CheckForNull String includes, @CheckForNull String excludes, boolean useDefaultExcludes) {
        includeNonPatternList = new ArrayList<TokenizedPath>();
        includePatternList = new ArrayList<TokenizedPattern>();
        excludeNonPatternList = new ArrayList<TokenizedPath>();
        excludePatternList = new ArrayList<TokenizedPattern>();
        
        // TODO: useDefaultExcludes
        
        parsePattern(includes, includeNonPatternList, includePatternList);
        parsePattern(excludes, excludeNonPatternList, excludePatternList);
    }
    
    private void parsePattern(
            @CheckForNull String pattern,
            @Nonnull List<TokenizedPath> nonPatternList,
            @Nonnull List<TokenizedPattern> patternList
    ) {
        if (StringUtils.isBlank(pattern)) {
            return;
        }
        
        StringTokenizer tokens = new StringTokenizer(pattern,",");
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken().trim();
            if (StringUtils.isBlank(token)) {
                continue;
            }
            if (!SelectorUtils.hasWildcards(token)) {
                nonPatternList.add(new TokenizedPath(token));
            } else {
                patternList.add(new TokenizedPattern(token));
            }
        }
    }
    
    /**
     * Stores {@link VirtualFile} and its (relative) path.
     */
    public static class VirtualFileWithPathInfo {
        public final VirtualFile file;
        
        /**
         * path fragments for the file.
         * You can get the path by joining them with the path separator.
         */
        public final List<String> pathFragments;
        
        private final TokenizedPath path;
        
        private VirtualFileWithPathInfo(VirtualFile file) {
            this.pathFragments = Arrays.asList("");
            this.path = new TokenizedPath("");
            this.file = file;
        }
        private VirtualFileWithPathInfo(VirtualFileWithPathInfo parent, VirtualFile file) {
            this.pathFragments = new ArrayList<String>(parent.pathFragments);
            this.pathFragments.add(file.getName());
            this.path = new TokenizedPath(parent.path, file.getName());
            this.file = file;
        }
    }
    
    /**
     * @param dir
     * @return the list of matched files.
     * @throws IOException
     */
    public List<VirtualFileWithPathInfo> scanFile(VirtualFile dir) throws IOException {
        List<VirtualFileWithPathInfo> found = new ArrayList<VirtualFileWithPathInfo>();
        Deque<VirtualFileWithPathInfo> dirsToScan = new ArrayDeque<VirtualFileWithPathInfo>();
        dirsToScan.addFirst(new VirtualFileWithPathInfo(dir));
        
        while (!dirsToScan.isEmpty()) {
            VirtualFileWithPathInfo file = dirsToScan.removeFirst();
            if (!file.file.isDirectory()) {
                continue;
            }
            for (VirtualFile _subFile : file.file.list() ) {
                VirtualFileWithPathInfo subFile = new VirtualFileWithPathInfo(file, _subFile);
                if (!subFile.file.isDirectory()) {
                    if (isIncluded(subFile.path)) {
                        found.add(subFile);
                    }
                } else {        // directory
                    if (canIncluded(subFile.path)) {
                        dirsToScan.addFirst(subFile);
                    }
                }
            }
        }
        
        return found;
    }

    private boolean isIncluded(TokenizedPath path) {
        // NO if excluded
        if (excludeNonPatternList.contains(path)) {
            return false;
        }
        for (TokenizedPattern pattern : excludePatternList) {
            if (pattern.matchPath(path, false)) {
                return false;
            }
        }
        
        // YES if included
        if (includeNonPatternList.contains(path)) {
            return true;
        }
        for (TokenizedPattern pattern : includePatternList) {
            if (pattern.matchPath(path, false)) {
                return true;
            }
        }
        
        return false;
    }

    private boolean canIncluded(TokenizedPath path) {
        // NO if excluded
        for (TokenizedPath excludePath : excludeNonPatternList) {
            if (excludePath.toPattern().matchStartOf(path, false)) {
                return false;
            }
        }
        for (TokenizedPattern pattern : excludePatternList) {
            if (pattern.matchStartOf(path, false)) {
                return false;
            }
        }
        
        // YES if included
        for (TokenizedPath includePath : excludeNonPatternList) {
            if (includePath.toPattern().matchStartOf(path, false)) {
                return true;
            }
        }
        for (TokenizedPattern pattern : includePatternList) {
            if (pattern.matchStartOf(path, false)) {
                return true;
            }
        }
        
        return false;
    }

}
