/**
 * Feature: repository-prefix-refactor
 * Property 3: 存储类型到前缀的双射映射 (Validates: Requirements 4.1, 6.1, 6.2)
 * Property 2: 前缀剥离正确性（前端） (Validates: Requirements 5.1)
 */
import fc from 'fast-check';
import { getResourcePrefix, stripResourcePrefix } from '../Utils.js';

// 所有合法的存储类型及其期望前缀
const STORAGE_PREFIX_MAP = {
  db: 'dbr:',
  jcr: 'jcr:',
};
const VALID_TYPES = Object.keys(STORAGE_PREFIX_MAP);

describe('Property 3: 存储类型到前缀的双射映射', () => {
  afterEach(() => {
    delete window._storageType;
  });

  it('每个存储类型映射到正确的前缀', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...VALID_TYPES),
        (storageType) => {
          window._storageType = storageType;
          const prefix = getResourcePrefix();
          expect(prefix).toBe(STORAGE_PREFIX_MAP[storageType]);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('不同存储类型产生不同前缀（单射性）', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...VALID_TYPES),
        fc.constantFrom(...VALID_TYPES),
        (typeA, typeB) => {
          fc.pre(typeA !== typeB);
          window._storageType = typeA;
          const prefixA = getResourcePrefix();
          window._storageType = typeB;
          const prefixB = getResourcePrefix();
          expect(prefixA).not.toBe(prefixB);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('无效或未设置的 storageType 快速失败抛错', () => {
    fc.assert(
      fc.property(
        fc.oneof(
          fc.constant(undefined),
          fc.constant(null),
          fc.constant(''),
          fc.string().filter((s) => !VALID_TYPES.includes(s))
        ),
        (badType) => {
          window._storageType = badType;
          expect(() => getResourcePrefix()).toThrow();
        }
      ),
      { numRuns: 100 }
    );
  });
});

// Feature: repository-prefix-refactor, Property 2: 前缀剥离正确性（前端）
// Validates: Requirements 5.1

const KNOWN_PREFIXES = ['jcr:', 'dbr:'];

/**
 * 生成不以已知前缀开头的任意字符串，用作路径主体
 */
const arbPathBody = fc.string({ minLength: 0, maxLength: 200 })
  .filter((s) => !KNOWN_PREFIXES.some((p) => s.startsWith(p)));

describe('Property 2: 前缀剥离正确性（前端）', () => {
  it('jcr: + S 剥离后等于 S', () => {
    fc.assert(
      fc.property(arbPathBody, (s) => {
        expect(stripResourcePrefix('jcr:' + s)).toBe(s);
      }),
      { numRuns: 100 }
    );
  });

  it('dbr: + S 剥离后等于 S', () => {
    fc.assert(
      fc.property(arbPathBody, (s) => {
        expect(stripResourcePrefix('dbr:' + s)).toBe(s);
      }),
      { numRuns: 100 }
    );
  });

  it('剥离结果不以任何已知前缀开头', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...KNOWN_PREFIXES),
        arbPathBody,
        (prefix, s) => {
          const result = stripResourcePrefix(prefix + s);
          for (const p of KNOWN_PREFIXES) {
            expect(result.startsWith(p)).toBe(false);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('无前缀路径原样返回', () => {
    fc.assert(
      fc.property(arbPathBody, (s) => {
        expect(stripResourcePrefix(s)).toBe(s);
      }),
      { numRuns: 100 }
    );
  });
});
