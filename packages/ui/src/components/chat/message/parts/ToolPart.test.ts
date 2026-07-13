import { describe, expect, test } from 'bun:test';

import { getEffectiveToolOutput, renderTerminalOutput } from './toolOutputHelpers';
import { readTaskTagSessionIdFromOutput } from './taskSessionIdParser';

describe('getEffectiveToolOutput', () => {
    test('prefers state.output for completed tools', () => {
        expect(getEffectiveToolOutput('bash', 'completed', 'final output', 'partial output')).toBe('final output');
    });

    test('falls back to metadata.output for running bash tools', () => {
        expect(getEffectiveToolOutput('bash', 'running', undefined, 'partial output')).toBe('partial output');
    });

    test('returns undefined for running non-bash tools even when metadata.output exists', () => {
        expect(getEffectiveToolOutput('read', 'running', undefined, 'partial output')).toBe(undefined);
    });

    test('returns undefined when running bash has no metadata.output', () => {
        expect(getEffectiveToolOutput('bash', 'running', undefined, undefined)).toBe(undefined);
    });

    test('ignores empty metadata.output for running bash', () => {
        expect(getEffectiveToolOutput('bash', 'running', undefined, '')).toBe(undefined);
    });
});

describe('renderTerminalOutput', () => {
    test('renders carriage-return progress updates as their latest value', () => {
        expect(renderTerminalOutput('Downloading 10%\r\u001B[2KDownloading 90%')).toBe('Downloading 90%');
    });

    test('removes ANSI styles while preserving the output text', () => {
        expect(renderTerminalOutput('\u001B[32mComplete\u001B[0m\n')).toBe('Complete\n');
    });

    test('applies cursor-up progress updates to the prior line', () => {
        expect(renderTerminalOutput('First\nWorking\u001B[1A\r\u001B[2KDone\n')).toBe('Done\nWorking');
    });
});

describe('readTaskTagSessionIdFromOutput', () => {
    test('parses task tags without state attributes', () => {
        expect(readTaskTagSessionIdFromOutput('<task id="ses_abc123">')).toBe('ses_abc123');
    });

    test('parses task tags with additional attributes', () => {
        expect(readTaskTagSessionIdFromOutput('<task id="ses_def456" state="completed">')).toBe('ses_def456');
    });
});
