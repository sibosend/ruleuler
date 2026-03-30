/**
 * Feature: general-entity-parameter, Property 2: 新建分类默认类型为 GeneralEntity
 * Validates: Requirements 2.1
 *
 * 对任意初始 master 数据状态，执行 ADD_MASTER 后新增分类的 type 为 'GeneralEntity'。
 */
import fc from 'fast-check';
import { ADD_MASTER, LOAD_MASTER_COMPLETED } from '../action.js';

// 直接提取 master reducer（它是 combineReducers 的子 reducer）
// 为了测试纯函数，我们手动 import reducer 并只测 master 分支
import rootReducer from '../reducer.js';

// 生成任意合法的分类对象
const arbCategory = fc.record({
  name: fc.string({ minLength: 0, maxLength: 20 }),
  clazz: fc.string({ minLength: 0, maxLength: 50 }),
  type: fc.constantFrom('Custom', 'Clazz', 'GeneralEntity'),
  variables: fc.array(
    fc.record({
      name: fc.string({ minLength: 0, maxLength: 10 }),
      label: fc.string({ minLength: 0, maxLength: 10 }),
      type: fc.constantFrom('String', 'Integer', 'Double', 'Boolean', 'Date'),
    }),
    { minLength: 0, maxLength: 5 }
  ),
});

describe('Property 2: 新建分类默认类型为 GeneralEntity', () => {
  it('对任意初始 master 数据，ADD_MASTER 后新增分类 type 为 GeneralEntity', () => {
    fc.assert(
      fc.property(
        fc.array(arbCategory, { minLength: 0, maxLength: 10 }),
        (initialData) => {
          // 先通过 LOAD_MASTER_COMPLETED 设置初始状态
          const stateAfterLoad = rootReducer(undefined, {
            type: LOAD_MASTER_COMPLETED,
            masterData: initialData,
          });

          // 执行 ADD_MASTER
          const stateAfterAdd = rootReducer(stateAfterLoad, { type: ADD_MASTER });

          const masterData = stateAfterAdd.master.data;

          // 新增的分类是最后一个
          expect(masterData.length).toBe(initialData.length + 1);

          const newCategory = masterData[masterData.length - 1];
          expect(newCategory.type).toBe('GeneralEntity');
        }
      ),
      { numRuns: 100 }
    );
  });
});
