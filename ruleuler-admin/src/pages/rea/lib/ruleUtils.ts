import type { RuleState } from '../types';

let ruleCounter = 0;

export function createDefaultRule(): RuleState {
  ruleCounter++;
  return {
    id: `rule-${Date.now()}-${ruleCounter}`,
    name: `rule${ruleCounter}`,
    properties: {},
    conditionText: '',
    actionText: '',
    elseText: '',
    conditionError: false,
    actionError: false,
    elseError: false,
  };
}

export function addRule(rules: RuleState[]): RuleState[] {
  return [...rules, createDefaultRule()];
}
