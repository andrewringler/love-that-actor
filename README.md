Love that Actor
=====================================

Scala play web application that recommends movies based on actors and movies you have liked

Developers
----------
	> pvm use 2.1.0
	> export TMDB_KEY=your developer key from http://www.themoviedb.org/
	> play
	[love-that-actor] $ eclipse with-source=true
	> run
	
	visit: http://localhost:9000

Responsive Images
----------
For all image assets I am displaying them at half their native resolution. For example, the following image
with the "medium" class and the following CSS:

	img.medium {
		max-width: 150px;
	}
   
Should be an asset with a minimum width of 300px. IE a JPG 300x300 or 300x200, etc… I didn't invent this idea, more on this here: [Responsive Images: What We Thought We Needed](http://24ways.org/2012/responsive-images-what-we-thought-we-needed/)