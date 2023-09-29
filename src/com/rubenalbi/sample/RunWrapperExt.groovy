package com.github.jglick.sample
public class RunWrapperExt {
    def delegate
    def steps
    public RunWrapperExt(delegate, steps) {
        this.delegate = delegate
        this.steps = steps
    }
    @NonCPS
    public boolean hasChangeIn(String pathPrefix) {
        steps.echo "pathPrefix=${pathPrefix}"
        for (hudson.scm.ChangeLogSet set : delegate.changeSets) {
            steps.echo "set=${set}"
            for (hudson.scm.ChangeLogSet.Entry entry : set) {
                steps.echo "entry=${entry}"
                for (String path : entry.affectedPaths) {
                    steps.echo "path=${path}"
                    if (path == pathPrefix || path.startsWith(pathPrefix + '/')) {
                        steps.echo "match"
                        return true
                    }
                }
            }
        }
        steps.echo "no match"
        false
    }
}
