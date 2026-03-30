/**
 * Feature: general-entity-parameter, Property 3: GeneralEntity 类型变量 label 自动等于 name
 * Validates: Requirements 3.2
 *
 * 对任意非空 name 字符串，saveData 逻辑应将 GeneralEntity 类型变量的 label 设为 name。
 */
import fc from 'fast-check';
import { saveData } from '../action.js';

// mock 外部依赖
beforeAll(() => {
  global.bootbox = { alert: jest.fn(), prompt: jest.fn() };
  global.window._server = 'http://localhost';
  global.$ = { ajax: jest.fn() };
});

const arbName = fc.string({ minLength: 1, maxLength: 30 }).filter(s => s.trim().length > 0);
const arbDatatype = fc.constantFrom('String', 'Integer', 'Double', 'Boolean', 'Date');

describe('Property 3: GeneralEntity 类型变量 label 自动等于 name', () => {
  it('对任意非空 name，saveData 将 GeneralEntity 变量的 label 设为 name', () => {
    fc.assert(
      fc.property(
        arbName,
        arbName,
        fc.array(
          fc.record({ name: arbName, label: fc.string(), type: arbDatatype }),
          { minLength: 1, maxLength: 5 }
        ),
        (catName, clazz, variables) => {
          const data = [{
            name: catName,
            clazz: clazz,
            type: 'GeneralEntity',
            variables: variables.map(v => ({ ...v })),
          }];

          saveData(data, false, 'test.xml');

          for (const variable of data[0].variables) {
            expect(variable.label).toBe(variable.name);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});
