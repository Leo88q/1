/**
 * Тесты механизма: правила против канона кварталов.
 *
 * Проверяем не «код работает», а «канон сходится»: заложенное решение действительно
 * собирает квартал, стартовая раскладка не собрана, план по ходам совпадает с
 * записанным в контенте, кресты не вращаются.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  boardFromLevel,
  cloneBoard,
  componentCount,
  isSolved,
  nextHint,
  parMoves,
  poweredCells,
  receiversPowered,
  rotate,
  tileMask,
  validateLevel,
  rotateCW,
  orbitSize,
  type LevelDef,
} from '../src/index.ts';
import levelsJson from '../src/levels.json' with { type: 'json' };

const LEVELS = (levelsJson as { levels: LevelDef[] }).levels;

test('канон кварталов проходит проверку контента', () => {
  assert.ok(LEVELS.length >= 12, 'в наборе должно быть не меньше 12 кварталов');
  for (const level of LEVELS) {
    assert.deepEqual(validateLevel(level), [], `квартал ${level.level}`);
  }
});

test('заложенное решение собирает квартал', () => {
  for (const level of LEVELS) {
    const board = boardFromLevel(level);
    board.rot = [...level.solutionRot];
    assert.equal(isSolved(board), true, `квартал ${level.level} не собирается решением`);
    assert.equal(componentCount(board), 1, `квартал ${level.level}: групп больше одной`);
    assert.equal(receiversPowered(board), level.receivers.length, `квартал ${level.level}: не все приёмники под током`);
  }
});

test('на старте квартал не собран', () => {
  for (const level of LEVELS) {
    assert.equal(isSolved(boardFromLevel(level)), false, `квартал ${level.level} уже собран на старте`);
    assert.ok(componentCount(boardFromLevel(level)) > 1, `квартал ${level.level}: одна группа на старте`);
  }
});

test('план по ходам совпадает с каноном', () => {
  for (const level of LEVELS) {
    const board = boardFromLevel(level);
    assert.equal(parMoves(board, level.solutionRot), level.parMoves, `квартал ${level.level}`);
  }
});

test('поворот: четыре шага возвращают исходное, кресты неподвижны', () => {
  const board = boardFromLevel(LEVELS[0]);
  for (let i = 0; i < board.base.length; i++) {
    const before = tileMask(board, i);
    if (board.fixed[i]) {
      assert.equal(rotate(board, i), false, `крест ${i} не должен вращаться`);
      assert.equal(tileMask(board, i), before);
      continue;
    }
    rotate(board, i);
    rotate(board, i);
    rotate(board, i);
    rotate(board, i);
    assert.equal(tileMask(board, i), before, `ячейка ${i}: поворот на 4 не вернул маску`);
  }
});

test('поворот по часовой двигает порты как rotateCW', () => {
  const board = boardFromLevel(LEVELS[1]);
  for (let i = 0; i < board.base.length; i++) {
    if (board.fixed[i]) continue;
    const before = tileMask(board, i);
    rotate(board, i, 1);
    assert.equal(tileMask(board, i), rotateCW(before), `ячейка ${i}`);
    rotate(board, i, -1);
    assert.equal(tileMask(board, i), before, `ячейка ${i}`);
  }
});

test('подсказка ведёт к решению за число ячеек, которые нужно довернуть', () => {
  for (const level of LEVELS.slice(0, 4)) {
    const board = boardFromLevel(level);
    const needing = board.rot.filter((r, i) => !board.fixed[i] && r !== level.solutionRot[i]).length;
    let hints = 0;
    for (let guard = 0; guard < board.rot.length; guard++) {
      const hint = nextHint(board, level.solutionRot);
      if (hint === null) break;
      hints += 1;
      // Доворачиваем по часовой до решения — как это сделает игрок.
      while (board.rot[hint] !== level.solutionRot[hint]) rotate(board, hint, 1);
    }
    assert.equal(nextHint(board, level.solutionRot), null, `квартал ${level.level}: подсказки не сошлись`);
    assert.ok(hints <= needing, `квартал ${level.level}: подсказок больше, чем ячеек к довороту`);
    assert.equal(isSolved(board), true, `квартал ${level.level}: после подсказок квартал не собран`);
  }
});

test('состояние копируется, история ходов не портит текущее поле', () => {
  const level = LEVELS[2];
  const current = boardFromLevel(level);
  const history = cloneBoard(current);
  rotate(current, 0, 1);
  assert.notDeepEqual(current.rot, history.rot, 'копия не должна меняться вместе с полем');
});

test('потерянные порты уменьшают число ячеек под током', () => {
  const level = LEVELS[0];
  const board = boardFromLevel(level);
  board.rot = [...level.solutionRot];
  assert.equal(poweredCells(board).every(Boolean), true);
  rotate(board, level.bus === 0 ? 1 : 0, 1); // поворот соседней с шиной ячейки рвёт контур
  const poweredAfter = poweredCells(board).filter(Boolean).length;
  assert.ok(poweredAfter < board.base.length, 'после поворота часть квартала должна остаться без тока');
});

test('орбиты поворотов: крест 1, прямая 2, обычный фрагмент 4', () => {
  assert.equal(orbitSize(15), 1);
  assert.equal(orbitSize(5), 2);
  assert.equal(orbitSize(7), 4);
  assert.equal(orbitSize(3), 4);
  assert.equal(orbitSize(1), 4);
});
