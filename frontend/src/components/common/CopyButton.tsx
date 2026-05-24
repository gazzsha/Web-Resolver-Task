import { useState, useCallback } from 'react';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';

export interface CopyButtonProps {
  text: string;
  label?: string;
  size?: 'small' | 'medium';
}

/**
 * Compact inline "copy to clipboard" button.
 * Shows a checkmark tooltip for 1.5 s after a successful copy.
 * Falls back to execCommand when navigator.clipboard is unavailable.
 */
const CopyButton: React.FC<CopyButtonProps> = ({ text, label, size = 'small' }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    const doFallback = () => {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      try {
        document.execCommand('copy');
      } finally {
        document.body.removeChild(ta);
      }
    };

    if (navigator.clipboard) {
      try {
        await navigator.clipboard.writeText(text);
      } catch {
        doFallback();
      }
    } else {
      doFallback();
    }

    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }, [text]);

  const ariaLabel = label ? `Копировать ${label}` : 'Копировать в буфер';

  return (
    <Tooltip title={copied ? 'Скопировано' : ariaLabel} placement="top">
      <IconButton
        size={size}
        onClick={handleCopy}
        aria-label={ariaLabel}
        sx={{ color: copied ? 'success.main' : 'text.disabled', transition: 'color 0.2s' }}
      >
        {copied ? (
          <CheckIcon fontSize="inherit" />
        ) : (
          <ContentCopyIcon fontSize="inherit" />
        )}
      </IconButton>
    </Tooltip>
  );
};

export default CopyButton;
