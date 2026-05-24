import React, { useCallback, useEffect, useRef } from 'react';
import { Box } from '@mui/material';

export interface SplitterProps {
  orientation: 'vertical' | 'horizontal';
  size: number;
  min: number;
  max: number;
  onChange: (next: number) => void;
  ariaLabel: string;
}

const STEP = 16;
// Hit-zone is wider than the visible bar so users can grab it easily.
const HIT_ZONE = 12;
const VISIBLE = 4;

const Splitter: React.FC<SplitterProps> = ({
  orientation,
  size,
  min,
  max,
  onChange,
  ariaLabel,
}) => {
  const isVertical = orientation === 'vertical';

  // Stable refs for handlers + drag state so cleanup can detach window listeners
  // even if the component unmounts mid-drag (window-listener leak fix).
  const isDraggingRef = useRef(false);
  const startPosRef = useRef(0);
  const startSizeRef = useRef(0);
  const minRef = useRef(min);
  const maxRef = useRef(max);
  const onChangeRef = useRef(onChange);
  const moveRef = useRef<((ev: MouseEvent) => void) | null>(null);
  const upRef = useRef<(() => void) | null>(null);
  const prevBodyCursorRef = useRef<string | null>(null);
  const prevBodyUserSelectRef = useRef<string | null>(null);

  const [hovered, setHovered] = React.useState(false);
  const [dragging, setDragging] = React.useState(false);

  // Keep refs in sync with latest props/state
  useEffect(() => { minRef.current = min; }, [min]);
  useEffect(() => { maxRef.current = max; }, [max]);
  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);

  const clamp = (v: number) => Math.min(Math.max(v, minRef.current), maxRef.current);

  const restoreBodyStyles = () => {
    if (prevBodyCursorRef.current !== null) {
      document.body.style.cursor = prevBodyCursorRef.current;
      prevBodyCursorRef.current = null;
    }
    if (prevBodyUserSelectRef.current !== null) {
      document.body.style.userSelect = prevBodyUserSelectRef.current;
      prevBodyUserSelectRef.current = null;
    }
  };

  const detachListeners = () => {
    if (moveRef.current) {
      window.removeEventListener('mousemove', moveRef.current);
      moveRef.current = null;
    }
    if (upRef.current) {
      window.removeEventListener('mouseup', upRef.current);
      upRef.current = null;
    }
  };

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      isDraggingRef.current = true;
      startPosRef.current = isVertical ? e.clientX : e.clientY;
      startSizeRef.current = size;
      setDragging(true);

      // Pin body cursor + disable selection during drag — kills cursor flicker
      // and prevents text selection in editor/task-panel while dragging.
      prevBodyCursorRef.current = document.body.style.cursor;
      prevBodyUserSelectRef.current = document.body.style.userSelect;
      document.body.style.cursor = isVertical ? 'col-resize' : 'row-resize';
      document.body.style.userSelect = 'none';

      const onMouseMove = (ev: MouseEvent) => {
        if (!isDraggingRef.current) return;
        const currentPos = isVertical ? ev.clientX : ev.clientY;
        const delta = currentPos - startPosRef.current;
        // For horizontal splitter between editor (top) and list (bottom):
        // dragging UP (negative delta) grows the list, so we negate.
        const next = clamp(
          startSizeRef.current + (isVertical ? delta : -delta)
        );
        onChangeRef.current(next);
      };

      const onMouseUp = () => {
        isDraggingRef.current = false;
        setDragging(false);
        restoreBodyStyles();
        detachListeners();
      };

      moveRef.current = onMouseMove;
      upRef.current = onMouseUp;
      window.addEventListener('mousemove', onMouseMove);
      window.addEventListener('mouseup', onMouseUp);
    },
    [isVertical, size]
  );

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const grow = isVertical
        ? e.key === 'ArrowRight'
        : e.key === 'ArrowUp';
      const shrink = isVertical
        ? e.key === 'ArrowLeft'
        : e.key === 'ArrowDown';

      if (grow) {
        e.preventDefault();
        onChange(clamp(size + STEP));
      } else if (shrink) {
        e.preventDefault();
        onChange(clamp(size - STEP));
      } else if (e.key === 'Home') {
        e.preventDefault();
        onChange(min);
      } else if (e.key === 'End') {
        e.preventDefault();
        onChange(max);
      }
    },
    [isVertical, size, min, max, onChange]
  );

  // Cleanup on unmount: detach window listeners + restore body styles even if
  // the component unmounts mid-drag (e.g. responsive breakpoint change).
  useEffect(() => {
    return () => {
      isDraggingRef.current = false;
      detachListeners();
      restoreBodyStyles();
    };
  }, []);

  const isActive = hovered || dragging;

  return (
    <Box
      role="separator"
      aria-orientation={orientation}
      aria-valuemin={min}
      aria-valuemax={max}
      aria-valuenow={size}
      aria-label={ariaLabel}
      tabIndex={0}
      onMouseDown={handleMouseDown}
      onKeyDown={handleKeyDown}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      sx={{
        flexShrink: 0,
        // Hit zone — invisible padding around the visible bar
        width: isVertical ? `${HIT_ZONE}px` : '100%',
        height: isVertical ? '100%' : `${HIT_ZONE}px`,
        marginLeft: isVertical ? `-${(HIT_ZONE - VISIBLE) / 2}px` : 0,
        marginRight: isVertical ? `-${(HIT_ZONE - VISIBLE) / 2}px` : 0,
        marginTop: isVertical ? 0 : `-${(HIT_ZONE - VISIBLE) / 2}px`,
        marginBottom: isVertical ? 0 : `-${(HIT_ZONE - VISIBLE) / 2}px`,
        cursor: isVertical ? 'col-resize' : 'row-resize',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        outline: 'none',
        '&:focus-visible': {
          outline: '2px solid',
          outlineColor: 'primary.main',
          outlineOffset: '-2px',
        },
        userSelect: 'none',
        touchAction: 'none',
        zIndex: 10,
        // Visible bar
        '&::before': {
          content: '""',
          width: isVertical ? `${VISIBLE}px` : '100%',
          height: isVertical ? '100%' : `${VISIBLE}px`,
          backgroundColor: isActive
            ? (theme) => theme.palette.primary.main
            : (theme) => theme.palette.divider,
          transition: 'background-color 0.15s',
        },
      }}
    />
  );
};

export default Splitter;
