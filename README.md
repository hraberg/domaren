# Domaren

*Domaren dömer (the judge judges)*
-- Ancient Swedish proverb

### ClojureScript [incremental-dom](google.github.io/incremental-dom) style library.

*No Docs. No Tests. No Features.*


## Overview

Incremental/Virtual DOM renderer in ClojureScript without any
dependencies like React. Stores various info in the DOM directly. Uses
[Hiccup](https://github.com/weavejester/hiccup) style syntax to
represent the tree. Like in
[Reagent](https://github.com/reagent-project/reagent), components are
specified using functions instead of keywords in the tree. Not
optimized. Naive support for `key` on children. Likely very buggy.

Aims to be dead simple. Does not concern itself with how and when the
app state atom is updated. Instead it always renders each frame using
`requestAnimationFrame`, but will try to avoid render
sub-trees. Caching of inputs to components are done straight in the
DOM, there are no cursors or special atoms.


## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).

To clean all compiled files:

    lein clean


## References

* https://facebook.github.io/react/docs/reconciliation.html
* http://google.github.io/incremental-dom/
* https://github.com/Matt-Esch/virtual-dom
* https://github.com/trueadm/inferno
* https://github.com/paldepind/snabbdom
* http://mithril.js.org/
* http://vdom-benchmark.github.io/vdom-benchmark/


## License

Copyright © 2015 Håkan Råberg

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
