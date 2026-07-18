(function () {
  'use strict';

  var games = [
    { id: 'catch', title: '接水果', subtitle: 'Catch the rhythm of falling color', number: '01', accent: '#06b6d4', accent2: '#0f766e' },
    { id: 'rps', title: '猜拳对决', subtitle: 'Read the moment. Make your move.', number: '02', accent: '#f43f5e', accent2: '#7c3aed' },
    { id: 'bubble', title: '戳泡泡', subtitle: 'Aim softly. Chain every touch.', number: '03', accent: '#84cc16', accent2: '#0891b2' },
    { id: 'tarot', title: '塔罗牌', subtitle: 'Three cards reveal one direction', number: '04', accent: '#d8b4fe', accent2: '#4338ca' },
    { id: 'ninja', title: '水果忍者', subtitle: 'Draw the blade through color', number: '05', accent: '#f97316', accent2: '#e11d48' },
    { id: 'rhythm', title: '节奏大师', subtitle: 'Move precisely inside the beat', number: '06', accent: '#a78bfa', accent2: '#0ea5e9' },
    { id: 'shooter', title: '星际突击', subtitle: 'A new path generated every run', number: '07', accent: '#38bdf8', accent2: '#1d4ed8' }
  ];
  var cardPerspective = document.getElementById('card-perspective');
  var dots = document.getElementById('dots');
  var stage = document.getElementById('card-stage');
  var cursor = document.getElementById('hand-cursor');
  var camera = document.getElementById('camera-preview');
  var cameraImage = document.getElementById('camera-image');
  var currentIndex = 0;
  var cards = [];
  var dotButtons = [];
  var dragStart = null;
  var dragX = 0;
  var wheelLocked = false;
  var timeField = null;

  function random(min, max) { return Math.random() * (max - min) + min; }
  function wrap(index) { return ((index % games.length) + games.length) % games.length; }

  function signedOffset(index, active) {
    var raw = index - active;
    var alternate = raw > 0 ? raw - games.length : raw + games.length;
    return Math.abs(alternate) < Math.abs(raw) ? alternate : raw;
  }

  function invokeJava(method) {
    var bridge = window.javaLobby;
    if (!bridge || typeof bridge[method] !== 'function') return false;
    var args = Array.prototype.slice.call(arguments, 1);
    try {
      bridge[method].apply(bridge, args);
      return true;
    } catch (error) {
      return false;
    }
  }

  function createTimeField() {
    var context = timeCanvas.getContext('2d', { alpha: false });
    var particles = [];
    var drawGroups = [];
    var alphaLevels = [.22, .42, .66, .92];
    var thicknessLevels = [.82, 1.28];
    var frame = 0;
    var running = false;
    var lastDraw = 0;
    var width = 1;
    var height = 1;
    var pixelRatio = 1;
    var targetFrameMs = 1000 / 24;
    var activeParticleCount = 340;
    var averageDrawCost = 0;
    var lastAdaptTime = 0;

    particleColors.forEach(function (color) {
      alphaLevels.forEach(function (alpha) {
        thicknessLevels.forEach(function (lineWidth) {
          drawGroups.push({
            color: color,
            alpha: alpha,
            lineWidth: lineWidth,
            segments: []
          });
        });
      });
    });

    for (var index = 0; index < 340; index += 1) {
      var pitch = random(-Math.PI, Math.PI);
      var angle = random(0, Math.PI * 2);
      particles.push({
        dx: Math.cos(angle),
        dy: Math.sin(angle),
        duration: random(1, 5),
        phase: random(0, 5),
        colorIndex: Math.floor(Math.random() * particleColors.length),
        depth: .2 + Math.abs(Math.cos(pitch)) * .8,
        brightness: random(.44, .98),
        thicknessIndex: Math.random() < .68 ? 0 : 1
      });
    }

    function resize() {
      width = Math.max(1, window.innerWidth);
      height = Math.max(1, window.innerHeight);
      pixelRatio = Math.min(Number(window.devicePixelRatio) || 1, 1,
        1280 / width, 720 / height);
      var bufferWidth = Math.max(1, Math.round(width * pixelRatio));
      var bufferHeight = Math.max(1, Math.round(height * pixelRatio));
      if (timeCanvas.width !== bufferWidth || timeCanvas.height !== bufferHeight) {
        timeCanvas.width = bufferWidth;
        timeCanvas.height = bufferHeight;
      }
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.fillStyle = '#000';
      context.fillRect(0, 0, width, height);
    }

    function draw(now) {
      if (!running) return;
      frame = window.requestAnimationFrame(draw);
      if (now - lastDraw < targetFrameMs) return;
      lastDraw = now;
      var drawStarted = performance.now();
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.globalAlpha = 1;
      context.fillStyle = '#000';
      context.fillRect(0, 0, width, height);

      var centerX = width * .5;
      var centerY = height * .5;
      var baseLength = Math.min(width, height) * .4;
      var seconds = now / 1000;
      drawGroups.forEach(function (group) { group.segments.length = 0; });
      for (var particleIndex = 0; particleIndex < activeParticleCount; particleIndex += 1) {
        var particle = particles[particleIndex];
        var progress = ((seconds + particle.phase) % particle.duration) / particle.duration;
        var scale = 2 * (1 - progress);
        var length = baseLength * scale * (.72 + particle.depth * .4);
        var alpha = progress < .2 ? progress / .2 : 1;
        alpha *= particle.brightness;
        if (length < .6 || alpha < .04) continue;

        var alphaIndex = Math.min(alphaLevels.length - 1,
          Math.max(0, Math.floor(alpha * alphaLevels.length)));
        var groupIndex = particle.colorIndex * alphaLevels.length * thicknessLevels.length
          + alphaIndex * thicknessLevels.length + particle.thicknessIndex;
        var segments = drawGroups[groupIndex].segments;
        var innerLength = length * .46;
        segments.push(
          centerX + particle.dx * innerLength,
          centerY + particle.dy * innerLength,
          centerX + particle.dx * length,
          centerY + particle.dy * length
        );
      }

      drawGroups.forEach(function (group) {
        if (!group.segments.length) return;
        context.beginPath();
        for (var segmentIndex = 0; segmentIndex < group.segments.length; segmentIndex += 4) {
          context.moveTo(group.segments[segmentIndex], group.segments[segmentIndex + 1]);
          context.lineTo(group.segments[segmentIndex + 2], group.segments[segmentIndex + 3]);
        }
        context.globalAlpha = group.alpha;
        context.strokeStyle = group.color;
        context.lineWidth = group.lineWidth;
        context.stroke();
      });
      context.globalAlpha = 1;

      var drawCost = performance.now() - drawStarted;
      averageDrawCost = averageDrawCost ? averageDrawCost * .9 + drawCost * .1 : drawCost;
      if (now - lastAdaptTime > 900) {
        if (averageDrawCost > 18 && activeParticleCount > 220) {
          activeParticleCount = Math.max(220, activeParticleCount - 20);
        } else if (averageDrawCost < 8 && activeParticleCount < particles.length) {
          activeParticleCount = Math.min(particles.length, activeParticleCount + 10);
        }
        lastAdaptTime = now;
      }
    }

    function start() {
      if (running) return;
      running = true;
      lastDraw = 0;
      frame = window.requestAnimationFrame(draw);
    }

    function stop() {
      running = false;
      if (frame) window.cancelAnimationFrame(frame);
      frame = 0;
    }

    window.addEventListener('resize', resize);
    resize();
    return { start: start, stop: stop, resize: resize };
  }

  function createCards() {
    games.forEach(function (game, index) {
      var card = document.createElement('article');
      card.className = 'fan-card';
      card.dataset.index = String(index);
      card.style.setProperty('--accent', game.accent);
      card.style.setProperty('--accent-2', game.accent2);
      card.innerHTML = '<div class="card-art"></div>'
        + '<div class="card-grain"></div>'
        + '<div class="card-sheen"></div>'
        + '<div class="card-copy"><span class="card-index">' + game.number + '</span>'
        + '<h2>' + game.title + '</h2><p>' + game.subtitle + '</p></div>';
      card.addEventListener('click', function () {
        if (dragStart && Math.abs(dragX) > 5) return;
        select(index, true);
      });
      cardPerspective.appendChild(card);
      cards.push(card);

      var dot = document.createElement('button');
      dot.type = 'button';
      dot.setAttribute('aria-label', '选择 ' + game.title);
      dot.addEventListener('click', function () { select(index, true); });
      dots.appendChild(dot);
      dotButtons.push(dot);
    });
  }

  function cardTransform(offset, cardWidth, visibleOffset) {
    var distance = Math.abs(offset);
    var sign = offset < 0 ? -1 : 1;
    if (distance > visibleOffset) {
      return 'translate(-50%, -50%) translate3d(' + (sign * window.innerWidth * .64)
        + 'px, 52px, -460px) rotateX(14deg) rotateZ(' + (sign * 50) + 'deg) scale(.82)';
    }
    var spacing = cardWidth * .47;
    var x = offset * spacing;
    var y = distance * 18 + (distance === 0 ? -22 : 0);
    var z = -distance * 150;
    var rotate = offset * 23;
    var scale = distance === 0 ? 1.035 : .94;
    return 'translate(-50%, -50%) translate3d(' + x + 'px,' + y + 'px,' + z
      + 'px) rotateX(' + (distance === 0 ? 0 : 12) + 'deg) rotateZ(' + rotate
      + 'deg) scale(' + scale + ')';
  }

  function cardSize() {
    var availableByHeight = Math.max(360, window.innerHeight * .68) / .6;
    var width = Math.min(620, window.innerWidth * .42, availableByHeight);
    width = Math.max(Math.min(420, window.innerWidth - 36), width);
    return {
      width: Math.round(width),
      height: Math.round(width * .6)
    };
  }

  function renderCards() {
    if (!cards.length) return;
    var size = cardSize();
    cards.forEach(function (card) {
      card.style.width = size.width + 'px';
      card.style.height = size.height + 'px';
    });
    var cardWidth = size.width;
    var visibleOffset = window.innerWidth < 1050 ? 1 : 2;
    cards.forEach(function (card, index) {
      var offset = signedOffset(index, currentIndex);
      var distance = Math.abs(offset);
      var visible = distance <= visibleOffset;
      var transform = cardTransform(offset, cardWidth, visibleOffset);
      if (index === currentIndex && dragStart) transform += ' translateX(' + dragX + 'px)';
      card.style.transform = transform;
      card.style.opacity = visible ? (distance === 0 ? '1' : distance === 1 ? '.9' : '.66') : '0';
      card.style.zIndex = String(50 - distance);
      card.style.pointerEvents = visible ? 'auto' : 'none';
      card.classList.toggle('is-active', distance === 0);
      card.classList.toggle('is-dragging', index === currentIndex && !!dragStart);
      card.dataset.baseTransform = cardTransform(offset, cardWidth, visibleOffset);
    });
    dotButtons.forEach(function (dot, index) {
      dot.classList.toggle('active', index === currentIndex);
    });
  }

  function select(index, notifyJava) {
    currentIndex = wrap(Math.round(index));
    renderCards();
    if (notifyJava) invokeJava('selectGame', currentIndex);
  }

  function navigate(direction) {
    if (!invokeJava('navigate', direction)) select(currentIndex + direction, false);
  }

  stage.addEventListener('keydown', function (event) {
    if (event.key === 'ArrowLeft') { event.preventDefault(); navigate(-1); }
    if (event.key === 'ArrowRight') { event.preventDefault(); navigate(1); }
    if (event.key === 'Enter') { event.preventDefault(); invokeJava('launchSelected'); }
  });

  stage.addEventListener('wheel', function (event) {
    event.preventDefault();
    if (wheelLocked || Math.abs(event.deltaY) + Math.abs(event.deltaX) < 8) return;
    wheelLocked = true;
    navigate((event.deltaY || event.deltaX) > 0 ? 1 : -1);
    window.setTimeout(function () { wheelLocked = false; }, 260);
  }, { passive: false });

  stage.addEventListener('pointerdown', function (event) {
    var activeCard = event.target.closest ? event.target.closest('.fan-card.is-active') : null;
    if (!activeCard) return;
    dragStart = { x: event.clientX, time: Date.now() };
    dragX = 0;
    stage.setPointerCapture(event.pointerId);
    renderCards();
  });

  stage.addEventListener('pointermove', function (event) {
    if (!dragStart) return;
    dragX = event.clientX - dragStart.x;
    renderCards();
  });

  function finishDrag(event) {
    if (!dragStart) return;
    var elapsed = Math.max(1, Date.now() - dragStart.time);
    var velocity = dragX / elapsed * 1000;
    var threshold = Math.min(145, cards[currentIndex].getBoundingClientRect().width * .2);
    var direction = dragX > threshold || velocity > 620 ? -1
      : dragX < -threshold || velocity < -620 ? 1 : 0;
    dragStart = null;
    dragX = 0;
    if (stage.hasPointerCapture(event.pointerId)) stage.releasePointerCapture(event.pointerId);
    if (direction) navigate(direction); else renderCards();
  }

  stage.addEventListener('pointerup', finishDrag);
  stage.addEventListener('pointercancel', finishDrag);
  window.addEventListener('resize', renderCards);
  document.getElementById('api-button').addEventListener('click', function () {
    invokeJava('openApiSettings');
  });

  window.gestureLobby = {
    setActive: function (index) { select(index, false); },
    setConfirmProgress: function (progress) {
      var value = Math.max(0, Math.min(1, Number(progress) || 0));
      cursor.style.setProperty('--confirm-progress', (value * 360) + 'deg');
      cursor.classList.toggle('is-confirming', value > .001);
    },
    setHand: function (visible, x, y) {
      cursor.classList.toggle('is-visible', !!visible);
      cursor.style.transform = 'translate3d(' + (Number(x) * window.innerWidth)
        + 'px,' + (Number(y) * window.innerHeight) + 'px,0)';
    },
    setGesture: function (visible, x, y, progress) {
      this.setHand(visible, x, y);
      this.setConfirmProgress(progress);
    },
    setCameraFrame: function (frame) {
      if (!frame) return;
      var value = String(frame);
      cameraImage.src = value.indexOf('data:') === 0 ? value : 'data:image/jpeg;base64,' + value;
      camera.classList.add('is-live');
    },
    activate: function () {
      document.getElementById('lobby').classList.remove('is-paused');
      timeField.start();
      stage.focus();
      renderCards();
    },
    pause: function () {
      document.getElementById('lobby').classList.add('is-paused');
      timeField.stop();
    }
  };

  // The continuously animated ray field is rendered by the native JavaFX
  // Canvas behind this transparent WebView. Keeping WebView static avoids the
  // intermittent Windows D3D RTTexture black-frame failure.
  timeField = { start: function () {}, stop: function () {}, resize: function () {} };
  createCards();
  renderCards();
}());
