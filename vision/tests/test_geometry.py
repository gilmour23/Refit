import unittest
import numpy as np
from test_contracts import load_function

class GeometryContract(unittest.TestCase):
    def test_geometry_is_in_decoded_original_pixels_and_excludes_missing_lines(self):
        geometry=load_function('measure_brace.py','geometry_view',{'np':np})
        view={'img':np.zeros((100,200,3)), 'f':.5,
              'pts':np.array([[20.,30.]]), 'marker':np.array([[1,2],[5,2],[5,4],[1,4]])}
        data=geometry(view,{1:(np.array([10.,20.]),np.array([30.,40.])),3:None},(200,400,3))
        self.assertEqual(data['width'],400);self.assertEqual(data['height'],200)
        self.assertEqual(data['lines']['1'],[[20.,40.],[60.,80.]])
        self.assertNotIn('3',data['lines']);self.assertEqual(data['wrist'],[40.,60.])
        self.assertEqual(data['card'][2],[10.,8.])
    def test_failure_geometry_still_has_original_extent(self):
        geometry=load_function('measure_brace.py','geometry_view',{'np':np})
        self.assertEqual(geometry({'f':1},{},(50,80,3)),
                         {'width':80,'height':50,'lines':{},'card':None,'wrist':None})
