For this Makefile to work, we need `cut` and `awk` to exist in the `$PATH` and they need to be
of the GNU kind.

If you're on a Mac, these tools are actually OpenBSD, and lack the features we use. The solution is to
install the Homebrew tools

brew install coreutils
brew install gawk

The Makefile will create symlinks named `awk` and `cut` in this directory and use those. See
gnu-coreutils-symlinks.sh to see how these are created.
