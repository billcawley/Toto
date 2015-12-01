(function($, window, document){
	// Requires Jquery
	if(!$) return false;	
	
	// Inspect Overlay
	var inspectOverlay = function(o_title){	
		var _ = this;
		_.initiated = false;
		_.el = null;
		_.numTabs = 0;
		_.o_title = o_title;
	
		// generate dialog, tabs, and then dynamically load the content
		_.init = function(){
			//console.log(_.win_title);
			_.el = $('<div class="overlay"><ul></ul></div>').hide().appendTo('body');
			_.el.ul = _.el.find('ul:first');
			_.el.dialog({
				modal		: 'true',
				/*width		: $('.tabs').width(),
				height		: $('.tabs').height(),
				position	: { my: "left top", at: "left top", of: $('.tabs') },
				*/
				minHeight	: 490,
				minWidth	: 780,
				title		: _.o_title,
				/*draggable	: false,
				resizeable	: false,*/
				close		: function(e, ui){
					_.close();
				},
				resize		: function(e, ui){
					// ensure iframe is 100% high
					var el = $(this).find('.dialog-inner')
					var ul = $(this).find('ul');
					
					el.innerHeight($(this).height() - ul.outerHeight(true));
					el.find('iframe').height(el.height() - 5);
				}
			});
			
			_.el.tabs({
				beforeLoad: function( event, ui ) {
					// prent reloading of tabs
					if ( ui.tab.data( "loaded" ) ) {
						event.preventDefault();
						return;
					}

					ui.jqXHR.success(function() {
						ui.tab.data( "loaded", true );
					});
				}
			});
			//_.el.siblings('.ui-dialog-titlebar').remove();
			_.el.show();
			
			// remove button
			_.el.delegate( "span.ui-icon-close", "click", function() {
				var panel = $( this ).closest( "li" ).remove().attr( "aria-controls" );
				$( "#" + panel ).remove();
				_.el.tabs( "refresh" );
				_.numTabs--;
				
				if(_.numTabs == 0) _.close(); // if last tab close dialog
				// else the last panel will automatically be selected
			});
			
			_.initiated = true;
			return _;
		};
		
		// new tab
		_.tab = function(url, title){
			if(!_.initiated) _.init();
			
			// if tab with url already exists then show it
			var existing = _.el.ul.find('a[data-url="' + url + '"]').parent();
			if(existing.length > 0){
				_.el.tabs( "option", "active", existing.index() );
				return _;
			}
			
			// now add the new tab 
			_.el.ul.append('<li><a data-url="' + url + '" href="#o-' + _.numTabs + '">' + title + '</a> <span class="ui-icon ui-icon-close">Remove</span></li>');
			_.el.append('<div id="o-' + _.numTabs + '" class="dialog-inner"><div class="loading"><h3>Loading Data...</h3> <div class="loader"><span class="fa fa-spin fa-cog"></span></div> </div><iframe src="' + url + '" width="100%" height="100%" frameborder="0" scrolling="auto" style="display:none"></iframe></div>');
			
			_.el.find('iframe').load(function(){
				_.el.find('.loading').hide();
				$(this).show();
			});
			
			_.el.tabs( "refresh" );
			_.numTabs++;
			
			// select the new tab
			_.el.tabs( "option", "active", _.numTabs-1 );
			
			return _;
		};
		
		// close and destroy
		_.close = function(){
			_.el.remove();
			$.inspectOverlay.current = null;
		}
	
		return _;
	}
	
	$.inspectOverlay = function(o_title){
		if(!($.inspectOverlay.current)){
			$.inspectOverlay.current = (new inspectOverlay(o_title));
		}
		
		return $.inspectOverlay.current;
	};
	$.inspectOverlay.current = null;
	
	
	// events
	$(document).ready(function(){
	
		// Date picker
		$( "#startDate" ).datepicker({
			defaultDate: "+1w",
			changeMonth: true,
			changeYear: true,
			onClose: function( selectedDate ) {
				$( "#endDate" ).datepicker( "option", "minDate", selectedDate );
			},
			dateFormat: 'yy-mm-dd'
		});
	
		$( "#endDate" ).datepicker({
			defaultDate: "+1w",
			changeMonth: true,
			changeYear: true,
			onClose: function( selectedDate ) {
				$( "#startDate" ).datepicker( "option", "maxDate", selectedDate );
			},
			dateFormat: 'yy-mm-dd'
		});
	
	
		// Inspect Overlay
		$('.inspect').on('click', function(e){
			e.preventDefault();
			$.inspectOverlay($(this).data('title')).tab($(this).attr('href'), $(this).data('title'));
		});
		
		
		// Memory Report
		$('.report').on('click', function(e){
			e.preventDefault();
			var el = $('<div class="overlay"></div>').hide().appendTo('body');
			var $this = $(this);
			
			el.dialog({
				modal	: 'true',
				width	: 'auto',
				title	: 'Memory Report'
			});
			
			// reload content every second
			function refresh(){
				el.load($this.attr('href'), {}, function(){
					// if dialog not open
					if(!el.dialog("isOpen")) return false;
				
					el.find('a').on('click', function(e){
						e.preventDefault();
						$this.attr('href', $(this).attr('href')); // override any links
					});
			
					setTimeout(refresh,1000);
				});
			}
			refresh();
			
			el.show();
		});
	});
	
})(window.jQuery, window, window.document);