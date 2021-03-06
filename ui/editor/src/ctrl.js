var chessground = require('chessground');
var partial = chessground.util.partial;
var editor = require('./editor');

module.exports = function(cfg) {

  this.data = editor.init(cfg);

  this.chessground = new chessground.controller({
    fen: cfg.fen,
    orientation: 'white',
    movable: {
      free: true,
      color: 'both',
      dropOff: 'trash'
    },
    animation: {
      duration: cfg.animation.duration
    },
    premovable: {
      enabled: false
    },
    draggable: {
      showGhost: false
    },
    events: {
      change: m.redraw
    }
  });

  this.computeFen = partial(editor.computeFen, this.data, this.chessground.getFen);

  this.trans = partial(editor.trans, this.data);

  this.startPosition = function() {
    this.chessground.set({
      fen: 'start'
    });
  }.bind(this);

  this.clearBoard = function() {
    this.chessground.set({
      fen: '8/8/8/8/8/8/8/8'
    });
  }.bind(this);

  this.loadNewFen = function(fen) {
    window.location = editor.makeUrl(this.data, fen);
  }.bind(this);
};
