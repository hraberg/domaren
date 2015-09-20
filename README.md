# domaren

ClojureScript [incremental-dom](google.github.io/incremental-dom) style library.

## Overview

Incremental/Virtual DOM renderer in ClojureScript without any dependencies like React. Stores various info in the DOM directly. Uses [Hiccup](https://github.com/weavejester/hiccup) style syntax to represent the tree. Like in [Reagent](https://github.com/reagent-project/reagent), components are specified using functions instead of keywords in the tree. Not optimized. No support for `key` in children yet. Likely very buggy.

Aims to be dead simple. Does not concern itself with how and when the app state atom is updated. Instead it always renders each frame using `requestAnimationFrame`, but will try to avoid render sub-trees. Caching of inputs to components are done straight in the DOM, there are no cursors or special atoms.


## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL.

## License

Copyright © 2015 Håkan Råberg

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
